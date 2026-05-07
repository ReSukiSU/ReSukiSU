#include <linux/anon_inodes.h>
#include <linux/err.h>
#include <linux/fdtable.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/kprobes.h>
#include <linux/pid.h>
#include <linux/slab.h>
#include <linux/syscalls.h>
#include <linux/uaccess.h>
#include <linux/version.h>

#ifdef CONFIG_KSU_SUSFS
#include <linux/namei.h>
#include <linux/susfs.h>
#endif // #ifdef CONFIG_KSU_SUSFS

#include "compat/kernel_compat.h"
#include "uapi/supercall.h"
#include "supercall/internal.h"
#include "arch.h"
#include "klog.h" // IWYU pragma: keep

static int anon_ksu_release(struct inode *inode, struct file *filp)
{
    pr_info("ksu fd released\n");
    return 0;
}

static long anon_ksu_ioctl(struct file *filp, unsigned int cmd, unsigned long arg)
{
    return ksu_supercall_handle_ioctl(cmd, (void __user *)arg);
}

static const struct file_operations anon_ksu_fops = {
    .owner = THIS_MODULE,
    .unlocked_ioctl = anon_ksu_ioctl,
    .compat_ioctl = anon_ksu_ioctl,
    .release = anon_ksu_release,
};

static void ksu_install_fd_to_user(int __user *outp)
{
    int fd = ksu_install_fd();
    pr_info("[%d] install ksu fd: %d\n", current->pid, fd);

    if (copy_to_user(outp, &fd, sizeof(fd))) {
        pr_err("install ksu fd reply err\n");
        do_close_fd(fd);
    }
}

// Install KSU fd to current process
int ksu_install_fd(void)
{
    struct file *filp;
    int fd;

    // Get unused fd
    fd = get_unused_fd_flags(O_CLOEXEC);
    if (fd < 0) {
        pr_err("ksu_install_fd: failed to get unused fd\n");
        return fd;
    }

    // Create anonymous inode file
    filp = anon_inode_getfile("[ksu_driver]", &anon_ksu_fops, NULL, O_RDWR | O_CLOEXEC);
    if (IS_ERR(filp)) {
        pr_err("ksu_install_fd: failed to create anon inode file\n");
        put_unused_fd(fd);
        return PTR_ERR(filp);
    }

    // Install fd
    fd_install(fd, filp);

    pr_info("ksu fd installed: %d for pid %d\n", fd, current->pid);

    return fd;
}

#ifdef CONFIG_KSU_TOOLKIT_SUPPORT
extern int ksu_try_handle_toolkit_cmd(int magic2, unsigned int cmd, void __user **arg);
#endif

#ifdef CONFIG_KSU_SUSFS
extern int ksu_handle_susfs_cmd(unsigned int cmd, void __user **arg);
#endif

// downstream: make sure to pass arg as reference, this can allow us to extend things.
int ksu_handle_sys_reboot(int magic1, int magic2, unsigned int cmd, void __user **arg)
{
    if (magic1 != KSU_INSTALL_MAGIC1)
        return -EINVAL;

#ifdef CONFIG_KSU_DEBUG
    pr_info("sys_reboot: intercepted call! magic: 0x%x id: %d\n", magic1, magic2);
#endif

    // Check if this is a request to install KSU fd
    if (magic2 == KSU_INSTALL_MAGIC2) {
        ksu_install_fd_to_user((int __user *)*arg);
        return 0;
    }

    // extensions

#if !defined(CONFIG_KSU_TOOLKIT_SUPPORT) && !defined(CONFIG_KSU_SUSFS)
    return 0;
#endif

    // other sys_reboot extensions are fully require uid 0,
    // so let's check it before
    if (ksu_get_uid_t(current_uid()) != 0)
        return 0;

#ifdef CONFIG_KSU_TOOLKIT_SUPPORT
    if (ksu_try_handle_toolkit_cmd(magic2, cmd, arg))
        return 0;
#endif

#ifdef CONFIG_KSU_SUSFS
    // If magic2 is susfs and current process is root
    if (magic2 == SUSFS_MAGIC) {
        return ksu_handle_susfs_cmd(cmd, arg);
    }
#endif
    return 0;
}

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
// Reboot hook for installing fd
static int reboot_handler_pre(struct kprobe *p, struct pt_regs *regs)
{
    struct pt_regs *real_regs = PT_REAL_REGS(regs);
    int magic1 = (int)PT_REGS_PARM1(real_regs);
    int magic2 = (int)PT_REGS_PARM2(real_regs);
    int cmd = (int)PT_REGS_PARM3(real_regs);
    void __user **arg = (void __user **)&PT_REGS_SYSCALL_PARM4(real_regs);

    ksu_handle_sys_reboot(magic1, magic2, cmd, arg);
    return 0;
}

static struct kprobe reboot_kp = {
    .symbol_name = REBOOT_SYMBOL,
    .pre_handler = reboot_handler_pre,
};
#endif

void __init ksu_supercalls_init(void)
{
    int rc;

    ksu_supercall_dump_commands();

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
    rc = register_kprobe(&reboot_kp);
    if (rc) {
        pr_err("reboot kprobe failed: %d\n", rc);
    } else {
        pr_info("reboot kprobe registered successfully\n");
    }
#endif
}

void __exit ksu_supercalls_exit(void)
{
#ifdef CONFIG_KSU_TRACEPOINT_HOOK
    unregister_kprobe(&reboot_kp);
#endif
    ksu_supercall_cleanup_state();
}
