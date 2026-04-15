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

ssize_t ksu_kernel_read_compat(struct file *p, void *buf, size_t count, loff_t *pos)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 14, 0) || defined(KSU_OPTIONAL_KERNEL_READ)
    return kernel_read(p, buf, count, pos);
#else // https://elixir.bootlin.com/linux/v4.14.336/source/fs/read_write.c#L418
    mm_segment_t old_fs;
    old_fs = get_fs();
    set_fs(get_ds());
    ssize_t result = vfs_read(p, (void __user *)buf, count, pos);
    set_fs(old_fs);
    return result;
#endif
}

ssize_t ksu_kernel_write_compat(struct file *p, const void *buf, size_t count, loff_t *pos)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 14, 0) || defined(KSU_OPTIONAL_KERNEL_WRITE)
    return kernel_write(p, buf, count, pos);
#else // https://elixir.bootlin.com/linux/v4.14.336/source/fs/read_write.c#L512
    mm_segment_t old_fs;
    old_fs = get_fs();
    set_fs(get_ds());
    ssize_t res = vfs_write(p, (__force const char __user *)buf, count, pos);
    set_fs(old_fs);
    return res;
#endif
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 8, 0) || defined(KSU_OPTIONAL_STRNCPY)
long ksu_strncpy_from_user_nofault(char *dst, const void __user *unsafe_addr, long count)
{
    return strncpy_from_user_nofault(dst, unsafe_addr, count);
}
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(5, 3, 0)
long ksu_strncpy_from_user_nofault(char *dst, const void __user *unsafe_addr, long count)
{
    return strncpy_from_unsafe_user(dst, unsafe_addr, count);
}
#else
// Copied from: https://elixir.bootlin.com/linux/v4.9.337/source/mm/maccess.c#L201
long ksu_strncpy_from_user_nofault(char *dst, const void __user *unsafe_addr, long count)
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
__weak int path_mount(const char *dev_name, struct path *path, const char *type_page, unsigned long flags,
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
    ret = do_mount(dev_name, (const char __user *)realpath, type_page, flags, data_page);
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
void *ksu_compat_kvrealloc(const void *p, size_t oldsize, size_t newsize, gfp_t flags)
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

// https://github.com/torvalds/linux/commit/e73f8959af0439d114847eab5a8a5ce48f1217c4
// this feature include in 3.5, but from my test, we no need that for 3.5-
// directly call is enough
#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 5, 0)
#include <linux/task_work.h>
struct ksu_task_work_struct {
    struct callback_head cb;
    void (*callback)(void *);
    void *data;
};

static void ksu_task_work_dispatcher(struct callback_head *cb)
{
    struct ksu_task_work_struct *tw = container_of(cb, struct ksu_task_work_struct, cb);
    tw->callback(tw->data);
    kfree(tw);
}
#endif

void ksu_run_in_init_if_possible(void (*callback)(void *), void *data)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 5, 0)
    struct task_struct *tsk;

    tsk = get_pid_task(find_vpid(1), PIDTYPE_PID);
    if (!tsk) {
        pr_err("ksu run in init find init task err\n");
        return;
    }

    // GFP_ATOMIC here, i don't want think the caller is in atomic context or not
    struct ksu_task_work_struct *tw = kzalloc(sizeof(struct ksu_task_work_struct), GFP_ATOMIC);
    if (!tw) {
        pr_err("ksu run in init alloc tw err\n");
        goto put_task;
    }

    tw->callback = callback;
    tw->data = data;
    tw->cb.func = ksu_task_work_dispatcher;

    if (task_work_add(tsk, &tw->cb, TWA_RESUME)) {
        kfree(tw);
        pr_warn("ksu run in init add task_work failed\n");
    }

put_task:
    put_task_struct(tsk);
#else
    callback(data);
#endif
}

#ifdef KSU_COMPAT_REQUIRE_SESSION_KEYRING
#include <linux/key.h>
#include <linux/errno.h>
#include <linux/cred.h>
#include "ksu.h"

static inline struct key *ksu_get_session_keyring(const struct cred *cred)
{
// https://github.com/torvalds/linux/commit/3a50597de8635cd05133bd12c95681c82fe7b878
#if LINUX_VERSION_CODE >= KERNEL_VERSION(3, 8, 0)
    return rcu_dereference(cred->session_keyring);
#else
    return rcu_dereference(current->cred->tgcred->session_keyring);
#endif
}

extern int install_session_keyring_to_cred(struct cred *, struct key *);

// https://github.com/torvalds/linux/commit/5c7e372caa35d303e414caeb64ee2243fd3cac3d
// in our target kernel version, it are protected by rcu, so let's rcu_dereference here
int ksu_key_permission(key_ref_t key_ref, const struct cred *cred, unsigned perm)
{
    if (ksu_get_session_keyring(ksu_cred)) {
        // if we have session_keyring, skip
        return 0;
    }

    if (strcmp(current->comm, "init")) {
        // we are only interested in `init` process
        return 0;
    }

    install_session_keyring_to_cred(ksu_cred, ksu_get_session_keyring(current_cred()));

    pr_info("kernel_compat: install init_session_keyring to ksu_cred\n");
    return 0;
}

#endif