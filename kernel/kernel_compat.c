#include <linux/version.h>
#include <linux/fs.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
#include <linux/sched/task.h>
#else
#include <linux/sched.h>
#endif
#include <linux/uaccess.h>
#include <linux/mm.h>
#include <linux/slab.h>
#include <linux/vmalloc.h>

#include "klog.h" // IWYU pragma: keep
#include "kernel_compat.h"

struct file *ksu_filp_open_compat(const char *filename, int flags, umode_t mode)
{
    return filp_open(filename, flags, mode);
}

ssize_t ksu_kernel_read_compat(struct file *p, void *buf, size_t count,
                               loff_t *pos)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 14, 0) ||                          \
    defined(KSU_OPTIONAL_KERNEL_READ)
    return kernel_read(p, buf, count, pos);
#else
    loff_t offset = pos ? *pos : 0;
    ssize_t result = kernel_read(p, offset, (char *)buf, count);
    if (pos && result > 0) {
        *pos = offset + result;
    }
    return result;
#endif
}

ssize_t ksu_kernel_write_compat(struct file *p, const void *buf, size_t count,
                                loff_t *pos)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 14, 0) ||                          \
    defined(KSU_OPTIONAL_KERNEL_WRITE)
    return kernel_write(p, buf, count, pos);
#else
    loff_t offset = pos ? *pos : 0;
    ssize_t result = kernel_write(p, buf, count, offset);
    if (pos && result > 0) {
        *pos = offset + result;
    }
    return result;
#endif
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 8, 0) ||                           \
    defined(KSU_OPTIONAL_STRNCPY)
long ksu_strncpy_from_user_nofault(char *dst, const void __user *unsafe_addr,
                                   long count)
{
    return strncpy_from_user_nofault(dst, unsafe_addr, count);
}
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(5, 3, 0)
long ksu_strncpy_from_user_nofault(char *dst, const void __user *unsafe_addr,
                                   long count)
{
    return strncpy_from_unsafe_user(dst, unsafe_addr, count);
}
#else
// Copied from: https://elixir.bootlin.com/linux/v4.9.337/source/mm/maccess.c#L201
long ksu_strncpy_from_user_nofault(char *dst, const void __user *unsafe_addr,
                                   long count)
{
    mm_segment_t old_fs = get_fs();
    long ret;

    if (unlikely(count <= 0))
        return 0;

    set_fs(USER_DS);
    pagefault_disable();
    ret = strncpy_from_user(dst, unsafe_addr, count);
    pagefault_enable();
    set_fs(old_fs);

    if (ret >= count) {
        ret = count;
        dst[ret - 1] = '\0';
    } else if (ret > 0) {
        ret++;
    }

    return ret;
}
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 9, 0)
__weak int path_mount(const char *dev_name, struct path *path,
                      const char *type_page, unsigned long flags,
                      void *data_page)
{
    // 384 is enough
    char buf[384] = { 0 };
    mm_segment_t old_fs;
    long ret;

    // -1 on the size as implicit null termination
    // as we zero init the thing
    char *realpath = d_path(path, buf, sizeof(buf) - 1);
    if (!(realpath && realpath != buf))
        return -ENOENT;

    old_fs = get_fs();
    set_fs(KERNEL_DS);
    ret = do_mount(dev_name, (const char __user *)realpath, type_page, flags,
                   data_page);
    set_fs(old_fs);
    return ret;
}
#endif

static void *__kvmalloc(size_t size, gfp_t flags)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 12, 0)
    // https://elixir.bootlin.com/linux/v4.4.302/source/security/apparmor/lib.c#L79
    void *buffer = NULL;

    if (size == 0)
        return NULL;

    /* do not attempt kmalloc if we need more than 16 pages at once */
    if (size <= (16 * PAGE_SIZE))
        buffer = kmalloc(size, flags | GFP_NOIO | __GFP_NOWARN);
    if (!buffer) {
        if (flags & __GFP_ZERO)
            buffer = vzalloc(size);
        else
            buffer = vmalloc(size);
    }
    return buffer;
#else
    return kvmalloc(size, flags);
#endif
}

#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 12, 0)
// https://elixir.bootlin.com/linux/v5.10.247/source/mm/util.c#L664
void *ksu_compat_kvrealloc(const void *p, size_t oldsize, size_t newsize,
                           gfp_t flags)
{
    void *newp;

    if (oldsize >= newsize)
        return (void *)p;
    newp = __kvmalloc(newsize, flags);
    if (!newp)
        return NULL;
    memcpy(newp, p, oldsize);
    kvfree(p);
    return newp;
}
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(3, 15, 0) &&                           \
    !defined(KSU_HAS_GET_CMDLINE)
// for the fucking sulog again
// https://github.com/torvalds/linux/commit/a90902531a06a030a252a07fbff7f45a189a64fe

/**
 * get_cmdline() - copy the cmdline value to a buffer.
 * @task:     the task whose cmdline value to copy.
 * @buffer:   the buffer to copy to.
 * @buflen:   the length of the buffer. Larger cmdline values are truncated
 *            to this length.
 * Returns the size of the cmdline field copied. Note that the copy does
 * not guarantee an ending NULL byte.
 */
int get_cmdline(struct task_struct *task, char *buffer, int buflen)
{
    int res = 0;
    unsigned int len;
    struct mm_struct *mm = get_task_mm(task);
    if (!mm)
        goto out;
    if (!mm->arg_end)
        goto out_mm; /* Shh! No looking before we're done */

    len = mm->arg_end - mm->arg_start;

    if (len > buflen)
        len = buflen;

    res = access_process_vm(task, mm->arg_start, buffer, len, 0);

    /*
	 * If the nul at the end of args has been overwritten, then
	 * assume application is using setproctitle(3).
	 */
    if (res > 0 && buffer[res - 1] != '\0' && len < buflen) {
        len = strnlen(buffer, res);
        if (len < res) {
            res = len;
        } else {
            len = mm->env_end - mm->env_start;
            if (len > buflen - res)
                len = buflen - res;
            res += access_process_vm(task, mm->env_start, buffer + res, len, 0);
            res = strnlen(buffer, res);
        }
    }
out_mm:
    mmput(mm);
out:
    return res;
}
#endif
