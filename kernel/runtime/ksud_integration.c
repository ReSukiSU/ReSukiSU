#include <linux/rcupdate.h>
#include <linux/slab.h>
#include <asm/current.h>
#include <linux/cred.h>
#include <linux/dcache.h>
#include <linux/err.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/version.h>
#include <linux/input.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0) && LINUX_VERSION_CODE < KERNEL_VERSION(5, 10, 0)
#include <linux/sched/task.h>
#endif
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 4, 0)
#include <linux/input-event-codes.h>
#else
#include <uapi/linux/input.h>
#endif
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 1, 0)
#include <linux/aio.h>
#endif
#include <linux/kprobes.h>
#include <linux/printk.h>
#include <linux/types.h>
#include <linux/uaccess.h>
#include <linux/namei.h>
#include <linux/workqueue.h>
#include <linux/uio.h>
#include <linux/module.h>
#include <linux/jump_label.h>
#include <linux/static_key.h>

#include "arch.h"
#include "klog.h" // IWYU pragma: keep
#include "ksu.h"
#include "ksud.h"
#include "ksud_boot.h"
#include "compat/kernel_compat.h"
#include "selinux/selinux.h"
#include "manager/throne_tracker.h"
#ifdef KSU_TP_HOOK
#include "hook/syscall_hook.h"
#endif

static const char KERNEL_SU_RC[] = "\n"

                                   "on post-fs-data\n"
                                   "	start logd\n"
                                   // We should wait for the post-fs-data finish
                                   "	exec u:r:" KERNEL_SU_DOMAIN ":s0 root -- " KSUD_PATH " post-fs-data\n"
                                   "\n"

                                   "on nonencrypted\n"
                                   "	exec u:r:" KERNEL_SU_DOMAIN ":s0 root -- " KSUD_PATH " services\n"
                                   "\n"

                                   "on property:vold.decrypt=trigger_restart_framework\n"
                                   "	exec u:r:" KERNEL_SU_DOMAIN ":s0 root -- " KSUD_PATH " services\n"
                                   "\n"

                                   "on property:sys.boot_completed=1\n"
                                   "	exec u:r:" KERNEL_SU_DOMAIN ":s0 root -- " KSUD_PATH " boot-completed\n"
                                   "\n"

                                   "\n";

static void stop_init_rc_hook(void);
static void stop_execve_hook(void);

#ifdef KSU_TP_HOOK
static struct work_struct stop_input_hook_work;
#else
bool ksu_init_rc_hook __read_mostly = true;
bool ksu_execveat_hook __read_mostly = true;
bool ksu_input_hook __read_mostly = true;
#endif

static const char __user *get_user_arg_ptr(struct user_arg_ptr argv, int nr)
{
    const char __user *native;

#ifdef CONFIG_COMPAT
    if (unlikely(argv.is_compat)) {
        compat_uptr_t compat;

        if (get_user(compat, argv.ptr.compat + nr))
            return ERR_PTR(-EFAULT);

        return compat_ptr(compat);
    }
#endif

    if (get_user(native, argv.ptr.native + nr))
        return ERR_PTR(-EFAULT);

    return native;
}

/*
 * count() counts the number of strings in array ARGV.
 */

/*
 * Make sure old GCC compiler can use __maybe_unused,
 * Test passed in 4.4.x ~ 4.9.x when use GCC.
 */

static int __maybe_unused count(struct user_arg_ptr argv, int max)
{
    int i = 0;

    if (argv.ptr.native != NULL) {
        for (;;) {
            const char __user *p = get_user_arg_ptr(argv, i);

            if (!p)
                break;

            if (IS_ERR(p))
                return -EFAULT;

            if (i >= max)
                return -E2BIG;
            ++i;

            if (fatal_signal_pending(current))
                return -ERESTARTNOHAND;
        }
    }
    return i;
}

static bool check_argv(struct user_arg_ptr argv, int index, const char *expected, char *buf, size_t buf_len)
{
    const char __user *p;
    int argc;

    argc = count(argv, MAX_ARG_STRINGS);
    if (argc <= index)
        return false;

    p = get_user_arg_ptr(argv, index);
    if (!p || IS_ERR(p))
        goto fail;

    if (ksu_strncpy_from_user_nofault(buf, p, buf_len) <= 0)
        goto fail;

    buf[buf_len - 1] = '\0';
    return !strcmp(buf, expected);

fail:
    pr_err("check_argv failed\n");
    return false;
}

// IMPORTANT NOTE: the call from execve_handler_pre WON'T provided correct value for envp and flags in GKI version
void ksu_handle_execveat_ksud(const char *filename, struct user_arg_ptr *argv, struct user_arg_ptr *envp, int *flags)
{
    static const char app_process[] = "/system/bin/app_process";
    static bool first_zygote = true;

    /* This applies to versions Android 10+ */
    static const char system_bin_init[] = "/system/bin/init";
    /* This applies to versions between Android 6 ~ 9  */
    static const char old_system_init[] = "/init";
    static bool init_second_stage_executed = false;

    // https://cs.android.com/android/platform/superproject/+/android-16.0.0_r2:system/core/init/main.cpp;l=77
    if (unlikely(!memcmp(filename, system_bin_init, sizeof(system_bin_init) - 1) && argv)) {
        // /system/bin/init executed
        char buf[16];
        if (!init_second_stage_executed && check_argv(*argv, 1, "second_stage", buf, sizeof(buf))) {
            pr_info("/system/bin/init second_stage executed via argv1 check\n");
            apply_kernelsu_rules();
            cache_sid();
            setup_ksu_cred();
            init_second_stage_executed = true;
        }
    } else if (unlikely(!memcmp(filename, old_system_init, sizeof(old_system_init) - 1) && argv)) {
        // /init executed
        int argc = count(*argv, MAX_ARG_STRINGS);
        pr_info("/init argc: %d\n", argc);
        if (argc > 1 && !init_second_stage_executed) {
            /* This applies to versions between Android 6 ~ 7 */
            char buf[16];
            if (!init_second_stage_executed && check_argv(*argv, 1, "--second-stage", buf, sizeof(buf))) {
                pr_info("/init second_stage executed via argv1 check\n");
                apply_kernelsu_rules();
                cache_sid();
                setup_ksu_cred();
                init_second_stage_executed = true;
            }
        } else if (argc == 1 && !init_second_stage_executed && envp) {
            int envc = count(*envp, MAX_ARG_STRINGS);
            if (envc > 0) {
                int n;
                for (n = 1; n <= envc; n++) {
                    const char __user *p = get_user_arg_ptr(*envp, n);
                    if (!p || IS_ERR(p)) {
                        continue;
                    }
                    char env[256];
                    // Reading environment variable strings from user space
                    if (ksu_strncpy_from_user_nofault(env, p, sizeof(env)) < 0)
                        continue;
                    // Parsing environment variable names and values
                    char *env_name = env;
                    char *env_value = strchr(env, '=');
                    if (env_value == NULL)
                        continue;
                    // Replace equal sign with string terminator
                    *env_value = '\0';
                    env_value++;
                    // Check if the environment variable name and value are matching
                    if (!strcmp(env_name, "INIT_SECOND_STAGE") &&
                        (!strcmp(env_value, "1") || !strcmp(env_value, "true"))) {
                        pr_info("/init second_stage executed via envp check\n");
                        apply_kernelsu_rules();
                        cache_sid();
                        setup_ksu_cred();
                        init_second_stage_executed = true;
                        break;
                    }
                }
            }
        }
    }

    if (unlikely(first_zygote && !memcmp(filename, app_process, sizeof(app_process) - 1) && argv)) {
        char buf[16];
        if (check_argv(*argv, 1, "-Xzygote", buf, sizeof(buf))) {
            pr_info("exec zygote, /data prepared, second_stage: %d\n", init_second_stage_executed);
            on_post_fs_data();
            first_zygote = false;
            ksu_stop_ksud_execve_hook();
        }
    }
}

static ssize_t (*orig_read)(struct file *, char __user *, size_t, loff_t *);
static ssize_t (*orig_read_iter)(struct kiocb *, struct iov_iter *);
static struct file_operations fops_proxy;
static ssize_t ksu_rc_pos = 0;
const size_t ksu_rc_len = sizeof(KERNEL_SU_RC) - 1;

// https://cs.android.com/android/platform/superproject/main/+/main:system/core/init/parser.cpp;l=144;drc=61197364367c9e404c7da6900658f1b16c42d0da
// https://cs.android.com/android/platform/superproject/main/+/main:system/libbase/file.cpp;l=241-243;drc=61197364367c9e404c7da6900658f1b16c42d0da
// The system will read init.rc file until EOF, whenever read() returns 0,
// so we begin append ksu rc when we meet EOF.

static ssize_t read_proxy(struct file *file, char __user *buf, size_t count, loff_t *pos)
{
    ssize_t ret = 0;
    size_t append_count;
    if (ksu_rc_pos && ksu_rc_pos < ksu_rc_len)
        goto append_ksu_rc;

    ret = orig_read(file, buf, count, pos);
    if (ret != 0 || ksu_rc_pos >= ksu_rc_len) {
        return ret;
    } else {
        pr_info("read_proxy: orig read finished, start append rc\n");
    }
append_ksu_rc:
    append_count = ksu_rc_len - ksu_rc_pos;
    if (append_count > count - ret)
        append_count = count - ret;
    // copy_to_user returns the number of not copied
    if (copy_to_user(buf + ret, KERNEL_SU_RC + ksu_rc_pos, append_count)) {
        pr_info("read_proxy: append error, totally appended %zd\n", ksu_rc_pos);
    } else {
        pr_info("read_proxy: append %zd\n", append_count);

        ksu_rc_pos += append_count;
        if (ksu_rc_pos == ksu_rc_len) {
            pr_info("read_proxy: append done\n");
        }
        ret += append_count;
    }

    return ret;
}

static ssize_t read_iter_proxy(struct kiocb *iocb, struct iov_iter *to)
{
    ssize_t ret = 0;
    size_t append_count;
    if (ksu_rc_pos && ksu_rc_pos < ksu_rc_len)
        goto append_ksu_rc;

    ret = orig_read_iter(iocb, to);
    if (ret != 0 || ksu_rc_pos >= ksu_rc_len) {
        return ret;
    } else {
        pr_info("read_iter_proxy: orig read finished, start append rc\n");
    }
append_ksu_rc:
    // copy_to_iter returns the number of copied bytes
    append_count = copy_to_iter(KERNEL_SU_RC + ksu_rc_pos, ksu_rc_len - ksu_rc_pos, to);
    if (!append_count) {
        pr_info("read_iter_proxy: append error, totally appended %zd\n", ksu_rc_pos);
    } else {
        pr_info("read_iter_proxy: append %zd\n", append_count);

        ksu_rc_pos += append_count;
        if (ksu_rc_pos == ksu_rc_len) {
            pr_info("read_iter_proxy: append done\n");
        }
        ret += append_count;
    }
    return ret;
}

static bool is_init_rc(struct file *fp)
{
    if (strcmp(current->comm, "init")) {
        // we are only interested in the `init` process.
        return false;
    }

    if (!d_is_reg(fp->f_path.dentry)) {
        return false;
    }

    const char *short_name = fp->f_path.dentry->d_name.name;
    if (strcmp(short_name, "init.rc")) {
        // we are only interested in the `init.rc` file name.
        return false;
    }
    char path[256];
    char *dpath = d_path(&fp->f_path, path, sizeof(path));

    if (IS_ERR(dpath)) {
        return false;
    }

    if (!!strcmp(dpath, "/init.rc") && !!strcmp(dpath, "/system/etc/init/hw/init.rc")) {
        return false;
    }

    return true;
}

#ifdef CONFIG_KSU_MANUAL_HOOK

// NOTE: https://github.com/tiann/KernelSU/commit/df640917d11dd0eff1b34ea53ec3c0dc49667002
// - added 260110, seems needed for A16 QPR 3

typedef enum {
    STAT_NATIVE, // struct stat
    STAT_COMPAT, // struct compat_stat
    STAT_STAT64 // struct stat64 // 32-bit uses this
} stat_type_t;

static __always_inline void ksu_common_newfstat_ret(unsigned long fd_long, void **statbuf_ptr, const int type)
{
    if (!ksu_init_rc_hook) {
        return;
    }

    if (!is_init(current_cred()))
        return;

    struct file *file = fget(fd_long);
    if (!file)
        return;

    if (!is_init_rc(file)) {
        fput(file);
        return;
    }
    fput(file);

    pr_info("%s: stat init.rc \n", __func__);

    uintptr_t statbuf_ptr_local = (uintptr_t) * (void **)statbuf_ptr;
    void __user *statbuf = (void __user *)statbuf_ptr_local;
    if (!statbuf)
        return;

    void __user *st_size_ptr;
    long size, new_size;
    size_t len;

    st_size_ptr = statbuf + offsetof(struct stat, st_size);
    len = sizeof(long);

#if defined(__ARCH_WANT_STAT64) || defined(__ARCH_WANT_COMPAT_STAT64)
    if (type) {
        st_size_ptr = statbuf + offsetof(struct stat64, st_size);
        len = sizeof(long long);
    }
#endif

    if (copy_from_user(&size, st_size_ptr, len)) {
        pr_info("%s: read statbuf 0x%lx failed \n", __func__, (unsigned long)st_size_ptr);
        return;
    }

    new_size = size + ksu_rc_len;
    pr_info("%s: adding ksu_rc_len: %ld -> %ld \n", __func__, size, new_size);

    if (!copy_to_user(st_size_ptr, &new_size, len))
        pr_info("%s: added ksu_rc_len \n", __func__);
    else
        pr_info("%s: add ksu_rc_len failed: statbuf 0x%lx \n", __func__, (unsigned long)st_size_ptr);

    return;
}

void ksu_handle_newfstat_ret(unsigned int *fd, struct stat __user **statbuf_ptr)
{
    unsigned long fd_long = (unsigned long)*fd;

    // native
    ksu_common_newfstat_ret(fd_long, (void **)statbuf_ptr, STAT_NATIVE);
}

#if defined(__ARCH_WANT_STAT64) || defined(__ARCH_WANT_COMPAT_STAT64)
void ksu_handle_fstat64_ret(unsigned long *fd, struct stat64 __user **statbuf_ptr)
{
    unsigned long fd_long = (unsigned long)*fd;

    // 32-bit call uses this!
    ksu_common_newfstat_ret(fd_long, (void **)statbuf_ptr, STAT_STAT64);
}
#endif

#endif

#ifdef CONFIG_KSU_SUSFS
void ksu_handle_vfs_fstat(int fd, loff_t *kstat_size_ptr)
{
    loff_t new_size = *kstat_size_ptr + ksu_rc_len;
    struct file *file = fget(fd);

    if (!file)
        return;

    if (is_init_rc(file)) {
        pr_info("stat init.rc");
        pr_info("adding ksu_rc_len: %lld -> %lld", *kstat_size_ptr, new_size);
        *kstat_size_ptr = new_size;
    }
    fput(file);
}
#endif // #ifdef CONFIG_KSU_SUSFS

void ksu_handle_initrc(struct file *file)
{
    if (!file) {
        return;
    }

// we no need this harden when using tracepoint hook
// because in tracepoint hook, this method always call by kprobe
// when we no need init rc hook, kprobe unregistered, and method never got call
#ifndef KSU_TP_HOOK
    if (!ksu_init_rc_hook)
        return;
#endif

    if (!is_init(current_cred()))
        return;

    if (!is_init_rc(file)) {
        return;
    }

    // we only process the first read
    static bool rc_hooked = false;
    if (rc_hooked) {
        // we don't need these hooks, unregister it!
        return;
    }
    rc_hooked = true;
    stop_init_rc_hook();

    // now we can sure that the init process is reading
    // `/init.rc` or `/system/etc/init/init.rc`

    pr_info("read init.rc, comm: %s, rc_count: %zu\n", current->comm, ksu_rc_len);

    // Now we need to proxy the read and modify the result!
    // But, we can not modify the file_operations directly, because it's in read-only memory.
    // We just replace the whole file_operations with a proxy one.
    memcpy(&fops_proxy, file->f_op, sizeof(struct file_operations));
    orig_read = file->f_op->read;
    if (orig_read) {
        fops_proxy.read = read_proxy;
    }
    orig_read_iter = file->f_op->read_iter;
    if (orig_read_iter) {
        fops_proxy.read_iter = read_iter_proxy;
    }
    // replace the file_operations
    file->f_op = &fops_proxy;
}

#ifndef CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK
static void ksu_handle_sys_read_fd(unsigned int fd)
{
    struct file *file = fget(fd);
    if (!file) {
        return;
    }

    ksu_handle_initrc(file);
    fput(file);
}
#endif

int ksu_handle_sys_read(unsigned int fd, char __user **buf_ptr, size_t *count_ptr)
{
#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK
    return 0; // dummy hook here
#else

#if defined(CONFIG_KSU_SUSFS) || defined(CONFIG_KSU_MANUAL_HOOK)
    if (!ksu_init_rc_hook) {
        return 0;
    }
#endif

    ksu_handle_sys_read_fd(fd);

    return 0;
#endif
}

static unsigned int volumedown_pressed_count = 0;

static bool is_volumedown_enough(unsigned int count)
{
    return count >= 3;
}

int ksu_handle_input_handle_event(unsigned int *type, unsigned int *code, int *value)
{
#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_INPUT_HOOK
    return 0; // dummy manual hook
#else

#if defined(CONFIG_KSU_SUSFS) || defined(CONFIG_KSU_MANUAL_HOOK)
    if (!ksu_input_hook) {
        return 0;
    }
#endif
    if (*type == EV_KEY && *code == KEY_VOLUMEDOWN) {
        int val = *value;
        pr_info("KEY_VOLUMEDOWN val: %d\n", val);
        if (val) {
            // key pressed, count it
            volumedown_pressed_count += 1;
            if (is_volumedown_enough(volumedown_pressed_count)) {
                ksu_stop_input_hook_runtime();
            }
        }
    }

    return 0;
#endif
}

#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_INPUT_HOOK
static void vol_detector_event(struct input_handle *handle, unsigned int type, unsigned int code, int value)
{
    if (!value)
        return;

    if (type != EV_KEY)
        return;

    if (code != KEY_VOLUMEDOWN)
        return;

    pr_info("KEY_VOLUMEDOWN press detected!\n");

    volumedown_pressed_count += 1;
    pr_info("volumedown_pressed_count: %d\n", volumedown_pressed_count);

    // yeah this fucks up, seems unreg in the same context is an issue
    // but then again, tehres no need to unreg here, just let on_post_fs_data do it
    //if (volume_pressed_count >= 3) {
    //	pr_info("KEY_VOLUMEDOWN pressed max times, safe mode detected!\n");
    //	ksu_stop_input_hook_runtime();
    //}
}

static int vol_detector_connect(struct input_handler *handler, struct input_dev *dev, const struct input_device_id *id)
{
    struct input_handle *handle;
    int error;

    handle = kzalloc(sizeof(struct input_handle), GFP_KERNEL);
    if (!handle)
        return -ENOMEM;

    handle->dev = dev;
    handle->handler = handler;
    handle->name = "ksu_handle_input";

    error = input_register_handle(handle);
    if (error)
        goto err_free_handle;

    error = input_open_device(handle);
    if (error)
        goto err_unregister_handle;

    return 0;

err_unregister_handle:
    input_unregister_handle(handle);
err_free_handle:
    kfree(handle);
    return error;
}

static const struct input_device_id vol_detector_ids[] = {
    {
        .flags = INPUT_DEVICE_ID_MATCH_EVBIT | INPUT_DEVICE_ID_MATCH_KEYBIT,
        .evbit = { BIT_MASK(EV_KEY) },
        .keybit = { [BIT_WORD(KEY_VOLUMEDOWN)] = BIT_MASK(KEY_VOLUMEDOWN) },
    },
    {}
};

static void vol_detector_disconnect(struct input_handle *handle)
{
    input_close_device(handle);
    input_unregister_handle(handle);
    kfree(handle);
}

MODULE_DEVICE_TABLE(input, vol_detector_ids);

static struct input_handler vol_detector_handler = {
    .event = vol_detector_event,
    .connect = vol_detector_connect,
    .disconnect = vol_detector_disconnect,
    .name = "ksu",
    .id_table = vol_detector_ids,
};

static int vol_detector_init()
{
    pr_info("vol_detector: init\n");
    return input_register_handler(&vol_detector_handler);
}

static void vol_detector_exit()
{
    pr_info("vol_detector: exit\n");
    input_unregister_handler(&vol_detector_handler);
}
#endif

DEFINE_STATIC_KEY_TRUE(ksud_execve_key);

void ksu_stop_ksud_execve_hook(void)
{
    static_branch_disable(&ksud_execve_key);
}

bool ksu_is_safe_mode()
{
    static bool safe_mode = false;
    if (safe_mode) {
        // don't need to check again, userspace may call multiple times
        return true;
    }

    if (ksu_late_loaded) {
        return false;
    }

    // stop hook first!
    ksu_stop_input_hook_runtime();

    pr_info("volumedown_pressed_count: %d\n", volumedown_pressed_count);
    if (is_volumedown_enough(volumedown_pressed_count)) {
        // pressed over 3 times
        pr_info("KEY_VOLUMEDOWN pressed max times, safe mode detected!\n");
        safe_mode = true;
        return true;
    }

    return false;
}

#ifdef KSU_TP_HOOK
void ksu_execve_hook_ksud(const struct pt_regs *regs)
{
    const char __user **filename_user = (const char **)&PT_REGS_PARM1(regs);
    const char __user *const __user *__argv = (const char __user *const __user *)PT_REGS_PARM2(regs);
    struct user_arg_ptr argv = { .ptr.native = __argv };
    char path[32];
    long ret;
    unsigned long addr;
    const char __user *fn;

    if (!filename_user)
        return;

    addr = untagged_addr((unsigned long)*filename_user);
    fn = (const char __user *)addr;

    memset(path, 0, sizeof(path));
    ret = strncpy_from_user(path, fn, 32);
    if (ret < 0) {
        pr_err("Access filename failed for execve_handler_pre\n");
        return;
    }

    ksu_handle_execveat_ksud(path, &argv, NULL, NULL);
}

static long (*orig_sys_read)(const struct pt_regs *regs);
static long ksu_sys_read(const struct pt_regs *regs)
{
    unsigned int fd = PT_REGS_PARM1(regs);
    char __user **buf_ptr = (char __user **)&PT_REGS_PARM2(regs);
    size_t *count_ptr = (size_t *)&PT_REGS_PARM3(regs);

    ksu_handle_sys_read(fd, buf_ptr, count_ptr);
    return orig_sys_read(regs);
}

static long (*orig_sys_fstat)(const struct pt_regs *regs);
static long ksu_sys_fstat(const struct pt_regs *regs)
{
    unsigned int fd = PT_REGS_PARM1(regs);
    void __user *statbuf = (void __user *)PT_REGS_PARM2(regs);
    bool is_rc = false;
    long ret;

    struct file *file = fget(fd);
    if (file) {
        if (is_init_rc(file)) {
            pr_info("stat init.rc");
            is_rc = true;
        }
        fput(file);
    }

    ret = orig_sys_fstat(regs);

    if (is_rc) {
        void __user *st_size_ptr = statbuf + offsetof(struct stat, st_size);
        long size, new_size;
        if (!copy_from_user_nofault(&size, st_size_ptr, sizeof(long))) {
            new_size = size + ksu_rc_len;
            pr_info("adding ksu_rc_len: %ld -> %ld", size, new_size);
            if (!copy_to_user_nofault(st_size_ptr, &new_size, sizeof(long))) {
                pr_info("added ksu_rc_len");
            } else {
                pr_err("add ksu_rc_len failed: statbuf 0x%lx", (unsigned long)st_size_ptr);
            }
        } else {
            pr_err("read statbuf 0x%lx failed", (unsigned long)st_size_ptr);
        }
    }

    return ret;
}

static int input_handle_event_handler_pre(struct kprobe *p, struct pt_regs *regs)
{
    unsigned int *type = (unsigned int *)&PT_REGS_PARM2(regs);
    unsigned int *code = (unsigned int *)&PT_REGS_PARM3(regs);
    int *value = (int *)&PT_REGS_CCALL_PARM4(regs);
    return ksu_handle_input_handle_event(type, code, value);
}

static struct kprobe input_event_kp = {
    .symbol_name = "input_event",
    .pre_handler = input_handle_event_handler_pre,
};

static void do_stop_input_hook(struct work_struct *work)
{
    unregister_kprobe(&input_event_kp);
}
#endif

static void stop_init_rc_hook(void)
{
#ifdef KSU_TP_HOOK
    ksu_syscall_table_unhook(__NR_read);
    ksu_syscall_table_unhook(__NR_fstat);
    pr_info("unregister init_rc syscall hook\n");
#else
    ksu_init_rc_hook = false;
    pr_info("stop init_rc_hook!\n");
#endif
}

void ksu_stop_input_hook_runtime(void)
{
    static bool input_hook_stopped = false;
    if (input_hook_stopped) {
        return;
    }
    input_hook_stopped = true;
#ifdef KSU_TP_HOOK
    bool ret = schedule_work(&stop_input_hook_work);
    pr_info("unregister input kprobe: %d!\n", ret);
#else
    ksu_input_hook = false;
    pr_info("stop input_hook\n");
#endif

#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_INPUT_HOOK
    vol_detector_exit();
#endif
}

// ksud: module support
void ksu_ksud_init(void)
{
#ifdef KSU_TP_HOOK
    int ret;

    ksu_syscall_table_hook(__NR_read, ksu_sys_read, &orig_sys_read);
    ksu_syscall_table_hook(__NR_fstat, ksu_sys_fstat, &orig_sys_fstat);

    ret = register_kprobe(&input_event_kp);
    pr_info("ksud: input_event_kp: %d\n", ret);

    INIT_WORK(&stop_input_hook_work, do_stop_input_hook);
#endif
#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_INPUT_HOOK
    vol_detector_init();
#endif
}

void ksu_ksud_exit(void)
{
#ifdef KSU_TP_HOOK
    // TODO:
    // this should be done before unregister vfs_read_kp
    // stop_init_rc_hook();
    unregister_kprobe(&input_event_kp);
#endif

#ifdef CONFIG_KSU_MANUAL_HOOK_AUTO_INPUT_HOOK
    vol_detector_exit();
#endif
    volumedown_pressed_count = 0;
}
