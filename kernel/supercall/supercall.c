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

#define KSU_DRIVER_PERMISSION_SU_SESSION (1UL << 0)

struct ksu_driver_context {
    unsigned long permissions;
};

static int anon_ksu_release(struct inode *inode, struct file *filp)
{
    kfree(filp->private_data);
    pr_info("ksu fd released\n");
    return 0;
}

static long anon_ksu_ioctl(struct file *filp, unsigned int cmd, unsigned long arg)
{
    return ksu_supercall_handle_ioctl(filp, cmd, (void __user *)arg);
}

static const struct file_operations anon_ksu_fops = {
    .owner = THIS_MODULE,
    .unlocked_ioctl = anon_ksu_ioctl,
    .compat_ioctl = anon_ksu_ioctl,
    .release = anon_ksu_release,
};

static int ksu_install_fd_with_permissions(unsigned int fd_flags, unsigned long permissions)
{
    struct ksu_driver_context *context;
    struct file *filp;
    const char *name;
    int fd;

    // alloc context
    context = kzalloc(sizeof(*context), GFP_KERNEL);
    if (!context)
        return -ENOMEM;

    context->permissions = permissions;
    name = permissions & KSU_DRIVER_PERMISSION_SU_SESSION ? "[ksu_driver_su]" : "[ksu_driver]";

    // Get unused fd
    fd = get_unused_fd_flags(fd_flags);
    if (fd < 0) {
        pr_err("%s: failed to get unused fd\n", __func__);
        kfree(context);
        return fd;
    }

    // Create anonymous inode file
    filp = anon_inode_getfile(name, &anon_ksu_fops, context, O_RDWR);
    if (IS_ERR(filp)) {
        pr_err("%s: failed to create anon inode file\n", __func__);
        put_unused_fd(fd);
        kfree(context);
        return PTR_ERR(filp);
    }

    // Install fd
    fd_install(fd, filp);

    pr_info("ksu fd installed: %d, name: %s, for pid %d\n", fd, name, current->pid);

    return fd;
}

int ksu_install_fd(void)
{
    return ksu_install_fd_with_permissions(O_CLOEXEC, 0);
}

int ksu_install_su_fd(void)
{
    // This descriptor must be installed after the exec into ksud.
    return ksu_install_fd_with_permissions(O_CLOEXEC, KSU_DRIVER_PERMISSION_SU_SESSION);
}

bool ksu_is_su_session_fd(const struct file *filp)
{
    const struct ksu_driver_context *context = filp->private_data;

    return context && (context->permissions & KSU_DRIVER_PERMISSION_SU_SESSION);
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
        int fd = ksu_install_fd();
        pr_info("[%d] install ksu fd: %d\n", current->pid, fd);

        if (copy_to_user((int __user *)*arg, &fd, sizeof(fd))) {
            pr_err("install ksu fd reply err\n");
            ksu_close_fd(fd);
        }
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
