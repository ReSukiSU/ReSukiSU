#ifndef __KSU_H_KERNEL_COMPAT
#define __KSU_H_KERNEL_COMPAT

#include <linux/namei.h>
#include <linux/cred.h>
#include <linux/fs.h>
#include <linux/err.h>
#include <linux/version.h>
#include <linux/fdtable.h>
#include "ss/policydb.h"
#include <linux/key.h>
#include <linux/ptrace.h>
#include <linux/syscalls.h>
#include "arch.h"

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 19, 0)
#if defined(__aarch64__)
#define KSU_SYS_PREFIX(name) __arm64_sys_##name
#elif defined(__x86_64__)
#define KSU_SYS_PREFIX(name) __x64_sys_##name
#elif defined(__arm__)
#define KSU_SYS_PREFIX(name) sys_##name
#else // wire up your arch here.
static_assert(1 == 0, "Unsupported architecture!");
#define KSU_SYS_PREFIX(name) sys_##name
#endif

/**
 * ksyscall: call syscalls from kernelspace
 * - tries to copy unistd's syscall()
 *
 * usage: ksyscall(close, fd);
 */
#define __ksyscall(name, a, b, c, d, e, f)                                                                             \
    ({                                                                                                                 \
        extern long KSU_SYS_PREFIX(name)(const struct pt_regs *);                                                      \
        struct pt_regs __ksu_regs = { 0 };                                                                             \
        PT_REGS_PARM1(&__ksu_regs) = (unsigned long)(a);                                                               \
        PT_REGS_PARM2(&__ksu_regs) = (unsigned long)(b);                                                               \
        PT_REGS_PARM3(&__ksu_regs) = (unsigned long)(c);                                                               \
        PT_REGS_SYSCALL_PARM4(&__ksu_regs) = (unsigned long)(d);                                                       \
        PT_REGS_PARM5(&__ksu_regs) = (unsigned long)(e);                                                               \
        PT_REGS_PARM6(&__ksu_regs) = (unsigned long)(f);                                                               \
        (long)KSU_SYS_PREFIX(name)(&__ksu_regs);                                                                       \
    })

// https://elixir.bootlin.com/musl/v1.2.6/source/src/internal/syscall.h#L45
#define ksyscall_0(name) __ksyscall(name, 0, 0, 0, 0, 0, 0)
#define ksyscall_1(name, a) __ksyscall(name, a, 0, 0, 0, 0, 0)
#define ksyscall_2(name, a, b) __ksyscall(name, a, b, 0, 0, 0, 0)
#define ksyscall_3(name, a, b, c) __ksyscall(name, a, b, c, 0, 0, 0)
#define ksyscall_4(name, a, b, c, d) __ksyscall(name, a, b, c, d, 0, 0)
#define ksyscall_5(name, a, b, c, d, e) __ksyscall(name, a, b, c, d, e, 0)
#define ksyscall_6(name, a, b, c, d, e, f) __ksyscall(name, a, b, c, d, e, f)

#else
#define KSU_SYS_PREFIX(name) sys_##name

#define ksyscall_0(name)                                                                                               \
    ({                                                                                                                 \
        extern typeof(KSU_SYS_PREFIX(name)) KSU_SYS_PREFIX(name);                                                      \
        (long)KSU_SYS_PREFIX(name)();                                                                                  \
    })

#define ksyscall_1(name, a)                                                                                            \
    ({                                                                                                                 \
        extern typeof(KSU_SYS_PREFIX(name)) KSU_SYS_PREFIX(name);                                                      \
        (long)KSU_SYS_PREFIX(name)(a);                                                                                 \
    })

#define ksyscall_2(name, a, b)                                                                                         \
    ({                                                                                                                 \
        extern typeof(KSU_SYS_PREFIX(name)) KSU_SYS_PREFIX(name);                                                      \
        (long)KSU_SYS_PREFIX(name)(a, b);                                                                              \
    })

#define ksyscall_3(name, a, b, c)                                                                                      \
    ({                                                                                                                 \
        extern typeof(KSU_SYS_PREFIX(name)) KSU_SYS_PREFIX(name);                                                      \
        (long)KSU_SYS_PREFIX(name)(a, b, c);                                                                           \
    })

#define ksyscall_4(name, a, b, c, d)                                                                                   \
    ({                                                                                                                 \
        extern typeof(KSU_SYS_PREFIX(name)) KSU_SYS_PREFIX(name);                                                      \
        (long)KSU_SYS_PREFIX(name)(a, b, c, d);                                                                        \
    })

#define ksyscall_5(name, a, b, c, d, e)                                                                                \
    ({                                                                                                                 \
        extern typeof(KSU_SYS_PREFIX(name)) KSU_SYS_PREFIX(name);                                                      \
        (long)KSU_SYS_PREFIX(name)(a, b, c, d, e);                                                                     \
    })

#define ksyscall_6(name, a, b, c, d, e, f)                                                                             \
    ({                                                                                                                 \
        extern typeof(KSU_SYS_PREFIX(name)) KSU_SYS_PREFIX(name);                                                      \
        (long)KSU_SYS_PREFIX(name)(a, b, c, d, e, f);                                                                  \
    })
#endif

#define __ksyscall_arg_n(_1, _2, _3, _4, _5, _6, _7, N, ...) N
#define __ksyscall_count_args(...) __ksyscall_arg_n(__VA_ARGS__, 6, 5, 4, 3, 2, 1, 0)
#define __ksyscall_concat(a, b) a##b
#define __ksyscall_exp(func, arg) __ksyscall_concat(func, arg)
#define ksyscall(...) __ksyscall_exp(ksyscall_, __ksyscall_count_args(__VA_ARGS__))(__VA_ARGS__)

#define ksu_close_fd(fd) ({ ksyscall(close, fd); })
#define ksu_sys_setns(fd, flags) ({ ksyscall(setns, fd, flags); })
#define ksu_sys_umount(mnt, flags) ({ ksyscall(umount, (char __user *)mnt, flags); })

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 19, 0)
#define ksys_unshare(flags) ({ ksyscall(unshare, flags); })
#endif

/*
 * Leagcy Huawei Hisi Devices info Start
 * For EMUI 9,9.1.0(Or HarmonyOS2 Based EMUI 9.1.0),10 Devices
 * Adapt to Huawei HISI kernel without affecting other kernels ,
 * Huawei Hisi Kernel EBITMAP Enable or Disable Flag ,
 * From ss/ebitmap.h
 */
#if ((LINUX_VERSION_CODE >= KERNEL_VERSION(4, 9, 0)) && (LINUX_VERSION_CODE < KERNEL_VERSION(4, 10, 0))) ||            \
    ((LINUX_VERSION_CODE >= KERNEL_VERSION(4, 14, 0)) && (LINUX_VERSION_CODE < KERNEL_VERSION(4, 15, 0)))
#if defined(HISI_SELINUX_EBITMAP_RO) && !defined(KSU_COMPAT_IS_HISI_HM2)
#define KSU_COMPAT_IS_HISI_LEGACY 1
#endif
#endif

/* 
* For EMUI 10+ or HarmonyOS2 Based EMUI10+ Devices
*/
#if (LINUX_VERSION_CODE >= KERNEL_VERSION(4, 14, 0)) && (LINUX_VERSION_CODE < KERNEL_VERSION(4, 15, 0))
#if defined(KSU_COMPAT_IS_HISI_HM2)
#define KSU_COMPAT_IS_HISI_LEGACY_HM2 1
#endif
#endif

/*
* Leagcy Huawei Hisi Devices info End
*/

// Checks for UH, KDP and RKP
#ifdef SAMSUNG_UH_DRIVER_EXIST
#if defined(CONFIG_UH) || defined(CONFIG_KDP) || defined(CONFIG_RKP)
#error                                                                                                                 \
    "CONFIG_UH, CONFIG_KDP and CONFIG_RKP is enabled! Please disable or remove it before compile a kernel with KernelSU!"
#endif
#endif

extern long ksu_strncpy_from_user_nofault(char *dst, const void __user *unsafe_addr, long count);

extern ssize_t ksu_kernel_read_compat(struct file *p, void *buf, size_t count, loff_t *pos);
extern ssize_t ksu_kernel_write_compat(struct file *p, const void *buf, size_t count, loff_t *pos);

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)
#define ksu_access_ok(addr, size) access_ok(addr, size)
#else
#define ksu_access_ok(addr, size) access_ok(VERIFY_READ, addr, size)
#endif

// https://elixir.bootlin.com/linux/v5.3-rc1/source/kernel/signal.c#L1613
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 3, 0)
#define __force_sig(sig) force_sig(sig)
#else
#define __force_sig(sig) force_sig(sig, current)
#endif

// Linux >= 5.7
// task_work_add (struct, struct, enum)
// Linux pre-5.7
// task_work_add (struct, struct, bool)
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 7, 0)
#ifndef TWA_RESUME
#define TWA_RESUME true
#endif
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(3, 9, 0) && !defined(KSU_UL_HAS_FILE_INODE)
static inline struct inode *file_inode(struct file *f)
{
    return f->f_path.dentry->d_inode;
}
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 1, 0) && !defined(KSU_OPTIONAL_SELINUX_INODE)
static inline struct inode_security_struct *selinux_inode(const struct inode *inode)
{
    return inode->i_security;
}
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 1, 0) && !defined(KSU_OPTIONAL_SELINUX_CRED)
static inline struct task_security_struct *selinux_cred(const struct cred *cred)
{
    return cred->security;
}
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 12, 0)
extern void *ksu_compat_kvrealloc(const void *p, size_t oldsize, size_t newsize, gfp_t flags);
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 15, 0)
__weak void groups_sort(struct group_info *group_info)
{
    return;
}
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 5, 0) && !defined(KSU_HAS_INODE_LOCK_UNLOCK)
// https://github.com/torvalds/linux/commit/5955102c9984fa081b2d570cfac75c97eecf8f3b
// for setuid_hooks only
// it will remove when we impl dynamic-manager feature init out of replaceable ksud
static inline void inode_lock(struct inode *inode)
{
    mutex_lock(&inode->i_mutex);
}

static inline void inode_unlock(struct inode *inode)
{
    mutex_unlock(&inode->i_mutex);
}
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(3, 14, 0)
#define ksu_get_uid_t(x) *(unsigned int *)&(x)
#else
#define ksu_get_uid_t(x) (x.val)
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(3, 18, 0)
__weak char *bin2hex(char *dst, const void *src, size_t count)
{
    const unsigned char *_src = src;
    while (count--)
        dst = pack_hex_byte(dst, *_src++);
    return dst;
}
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(3, 14, 0)
// https://github.com/torvalds/linux/commit/89a0714106aac7309c7dfa0f004b39e1e89d2942
// app_profile require U16_MAX, define here
#define U16_MAX ((u16)~0U)
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(3, 11, 0) && !defined(KSU_HAS_ITERATE_DIR)
struct dir_context {
    const filldir_t actor;
    loff_t pos;
};

static int iterate_dir(struct file *file, struct dir_context *ctx)
{
    return vfs_readdir(file, ctx->actor, ctx);
}
#endif // KSU_HAS_ITERATE_DIR

#if LINUX_VERSION_CODE < KERNEL_VERSION(3, 8, 0)
__weak long vfs_truncate(struct path *path, loff_t length)
{
    struct inode *inode;
    long error;

    inode = path->dentry->d_inode;

    /* For directories it's -EISDIR, for other non-regulars - -EINVAL */
    if (S_ISDIR(inode->i_mode))
        return -EISDIR;
    if (!S_ISREG(inode->i_mode))
        return -EINVAL;

    error = mnt_want_write(path->mnt);
    if (error)
        goto out;

    error = inode_permission(inode, MAY_WRITE);
    if (error)
        goto mnt_drop_write_and_out;

    error = -EPERM;
    if (IS_APPEND(inode))
        goto mnt_drop_write_and_out;

    error = get_write_access(inode);
    if (error)
        goto mnt_drop_write_and_out;

    /*
	 * Make sure that there are no leases.  get_write_access() protects
	 * against the truncate racing with a lease-granting setlease().
	 */
    error = break_lease(inode, O_WRONLY);
    if (error)
        goto put_write_and_out;

    error = locks_verify_truncate(inode, NULL, length);
    if (!error)
        error = security_path_truncate(path);
    if (!error)
        error = do_truncate(path->dentry, length, 0, NULL);

put_write_and_out:
    put_write_access(inode);
mnt_drop_write_and_out:
    mnt_drop_write(path->mnt);
out:
    return error;
}
#endif

// linux kernel https://github.com/torvalds/linux/commit/7e040726850a106587485c21bdacc0bfc8a0cbed
// kanged from xxksu lmao
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 12, 0) && !defined(EPOLLIN)
#define EPOLLIN 0x00000001
#define EPOLLPRI 0x00000002
#define EPOLLOUT 0x00000004
#define EPOLLERR 0x00000008
#define EPOLLHUP 0x00000010
#define EPOLLRDNORM 0x00000040
#define EPOLLRDBAND 0x00000080
#define EPOLLWRNORM 0x00000100
#define EPOLLWRBAND 0x00000200
#define EPOLLMSG 0x00000400
#define EPOLLRDHUP 0x00002000
#endif

#ifndef READ_ONCE
#define READ_ONCE(x) (*(const volatile typeof(x) *)&(x))
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(3, 15, 0)
#define task_ppid_nr(a) (pid_t) sys_getppid()
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(3, 17, 0)
static inline u64 ksu_ktime_get_ns(void)
{
    return ktime_to_ns(ktime_get());
}
#define ktime_get_ns ksu_ktime_get_ns
#endif

// WARNING: no overflow safety!
#ifndef struct_size
#define struct_size(p, member, n) (sizeof(*(p)) + (n) * sizeof(*(p)->member))
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 12, 0)
#ifndef ALIGN_DOWN
#define ALIGN_DOWN(x, a) __ALIGN_KERNEL((x) - ((a) - 1), (a))
#endif
#endif

#ifndef untagged_addr
#define untagged_addr(addr) (addr)
#endif

#ifndef in_compat_syscall
#define in_compat_syscall() is_compat_task()
#endif

extern void ksu_run_in_init_if_possible(void (*callback)(void *), void *data);

extern struct key *init_session_keyring;
extern void setup_ksu_cred_session_keyring(void);

static inline struct key *ksu_get_session_keyring(const struct cred *cred)
{
// https://github.com/torvalds/linux/commit/3a50597de8635cd05133bd12c95681c82fe7b878
#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 8, 0)
    return rcu_dereference(cred->session_keyring);
#else
    return rcu_dereference(current->cred->tgcred->session_keyring);
#endif
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 3, 0) || defined(KSU_HAS_MODERN_STATIC_KEY_INTERFACE)
#define KSU_COMPAT_USE_STATIC_KEY
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 8, 0)
__weak long copy_from_kernel_nofault(void *dst, const void *src, size_t size)
{
    // https://elixir.bootlin.com/linux/v5.2.21/source/mm/maccess.c#L27
    long ret;
    mm_segment_t old_fs = get_fs();

    set_fs(KERNEL_DS);
    pagefault_disable();
    ret = __copy_from_user_inatomic(dst, (__force const void __user *)src, size);
    pagefault_enable();
    set_fs(old_fs);

    return ret ? -EFAULT : 0;
}
#endif

#ifndef __nocfi
#define __nocfi
#endif

// https://github.com/torvalds/linux/commit/294f69e662d1570703e9b56e95be37a9fd3afba5
// f**k old compiler, thx for your notices, but better don't notice next time
#ifndef __GCC4_has_attribute___fallthrough__
#define __GCC4_has_attribute___fallthrough__ 0
#endif

#ifndef __has_attribute
#define __has_attribute(x) __GCC4_has_attribute_##x
#endif
/*
 * Add the pseudo keyword 'fallthrough' so case statement blocks
 * must end with any of these keywords:
 *   break;
 *   fallthrough;
 *   continue;
 *   goto <label>;
 *   return [expression];
 *
 *  gcc: https://gcc.gnu.org/onlinedocs/gcc/Statement-Attributes.html#Statement-Attributes
 */
#if __has_attribute(__fallthrough__)
#define fallthrough __attribute__((__fallthrough__))
#else
#define fallthrough                                                                                                    \
    do {                                                                                                               \
    } while (0) /* fallthrough */
#endif

#ifdef KSU_COMPAT_SYM_NAME_NOT_FOUND

#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 1, 0) && !defined(KSU_COMPAT_HAS_MODERN_POLICYDB)
#include <linux/flex_array.h>
#endif

static inline char *sym_name(struct policydb *p, unsigned int sym_num, unsigned int element_nr)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0) || defined(KSU_COMPAT_HAS_MODERN_POLICYDB)
    return p->sym_val_to_name[sym_num][element_nr];
#else
    struct flex_array *fa = p->sym_val_to_name[sym_num];

    return flex_array_get_ptr(fa, element_nr);
#endif
}
#endif

static inline struct file *ksu_filp_open_nonotify(const char *path, int flags)
{
    struct path p;
    struct file *f;
    int ret;
    ret = kern_path(path, (flags & O_NOFOLLOW) ? 0 : LOOKUP_FOLLOW, &p);
    if (ret) {
        return ERR_PTR(ret);
    }

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 14, 0)
    f = dentry_open_nonotify(&p, flags, current_cred());
    // https://github.com/torvalds/linux/commit/765927b2d508712d320c8934db963bbe14c3fcec
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(3, 6, 0) || defined(KSU_COMPAT_HAS_MODERN_DENTRY_OPEN)
    f = dentry_open(&p, flags | __FMODE_NONOTIFY, current_cred());
#else
    f = dentry_open(p.dentry, p.mnt, flags | __FMODE_NONOTIFY, current_cred());
#endif

    path_put(&p);
    return f;
}

#endif
