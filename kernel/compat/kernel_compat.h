#ifndef __KSU_H_KERNEL_COMPAT
#define __KSU_H_KERNEL_COMPAT

#include <linux/fs.h>
#include <linux/version.h>
#ifdef KSU_TP_HOOK
#include <linux/task_work.h>
#endif
#include <linux/fdtable.h>
#include "ss/policydb.h"
#include "linux/key.h"

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

static inline int do_close_fd(unsigned int fd)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 11, 0)
    return close_fd(fd);
#else
    return __close_fd(current->files, fd);
#endif
}

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

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 19, 0)
// kernel below 4.19 maybe not have 3 helper, but impl that is very easy
// copy from https://github.com/torvalds/linux/commit/c42b65e363ce97a828f81b59033c3558f8fa7f70
__weak unsigned long *bitmap_alloc(unsigned int nbits, gfp_t flags)
{
    return kmalloc_array(BITS_TO_LONGS(nbits), sizeof(unsigned long), flags);
}

__weak unsigned long *bitmap_zalloc(unsigned int nbits, gfp_t flags)
{
    return bitmap_alloc(nbits, flags | __GFP_ZERO);
}

__weak void bitmap_free(const unsigned long *bitmap)
{
    kfree(bitmap);
}
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

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 10, 0) || defined(KSU_COMPAT_IS_HISI_LEGACY) ||                             \
    defined(KSU_COMPAT_IS_HISI_LEGACY_HM2)
#define KSU_COMPAT_REQUIRE_SESSION_KEYRING
extern int ksu_key_permission(key_ref_t key_ref, const struct cred *cred, unsigned perm);
#endif

#endif