#ifndef __KSU_H_KERNEL_COMPAT
#define __KSU_H_KERNEL_COMPAT

#include <linux/fs.h>
#include <linux/version.h>
#include <linux/task_work.h>
#include <linux/fdtable.h>
#include "ss/policydb.h"
#include "linux/key.h"

/*
 * Adapt to Huawei HISI kernel without affecting other kernels ,
 * Huawei Hisi Kernel EBITMAP Enable or Disable Flag ,
 * From ss/ebitmap.h
 */
#if (LINUX_VERSION_CODE >= KERNEL_VERSION(4, 9, 0)) &&                         \
        (LINUX_VERSION_CODE < KERNEL_VERSION(4, 10, 0)) ||                     \
    (LINUX_VERSION_CODE >= KERNEL_VERSION(4, 14, 0)) &&                        \
        (LINUX_VERSION_CODE < KERNEL_VERSION(4, 15, 0))
#ifdef HISI_SELINUX_EBITMAP_RO
#define CONFIG_IS_HW_HISI
#endif
#endif

// Checks for UH, KDP and RKP
#ifdef SAMSUNG_UH_DRIVER_EXIST
#if defined(CONFIG_UH) || defined(CONFIG_KDP) || defined(CONFIG_RKP)
#error                                                                         \
    "CONFIG_UH, CONFIG_KDP and CONFIG_RKP is enabled! Please disable or remove it before compile a kernel with KernelSU!"
#endif
#endif

extern long ksu_strncpy_from_user_nofault(char *dst,
                                          const void __user *unsafe_addr,
                                          long count);

extern struct file *ksu_filp_open_compat(const char *filename, int flags,
                                         umode_t mode);
extern ssize_t ksu_kernel_read_compat(struct file *p, void *buf, size_t count,
                                      loff_t *pos);
extern ssize_t ksu_kernel_write_compat(struct file *p, const void *buf,
                                       size_t count, loff_t *pos);

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 10, 0) ||                           \
    defined(CONFIG_IS_HW_HISI) || defined(CONFIG_KSU_ALLOWLIST_WORKAROUND)
extern struct key *init_session_keyring;
#endif

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

#if LINUX_VERSION_CODE < KERNEL_VERSION(3, 9, 0) &&                            \
    !defined(KSU_UL_HAS_FILE_INODE)
static inline struct inode *file_inode(struct file *f)
{
    return f->f_path.dentry->d_inode;
}
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 1, 0) &&                            \
    !defined(KSU_OPTIONAL_SELINUX_INODE)
static inline struct inode_security_struct *
selinux_inode(const struct inode *inode)
{
    return inode->i_security;
}
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 1, 0) &&                            \
    !defined(KSU_OPTIONAL_SELINUX_CRED)
static inline struct task_security_struct *selinux_cred(const struct cred *cred)
{
    return cred->security;
}
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 12, 0)
extern void *ksu_compat_kvrealloc(const void *p, size_t oldsize, size_t newsize,
                                  gfp_t flags);
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

#ifndef KSU_HAS_INODE_LOCK_UNLOCK
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

#endif