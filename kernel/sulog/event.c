#include <asm/current.h>
#include <linux/compat.h>
#include <linux/cred.h>
#include <linux/gfp.h>
#include <linux/version.h>
// https://github.com/torvalds/linux/commit/b296a6d53339a79082c1d2c1761e948e8b3def69
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_MINMAX_H)
#include <linux/minmax.h>
#else
#include <linux/kernel.h>
#endif
#include <linux/sched.h> // signal in lower kernel in sched.h
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 10, 0)
#include <linux/sched/signal.h>
#endif
#include <linux/slab.h>
#include <linux/string.h>
#include <linux/uaccess.h>

#include <linux/version.h>
#if defined(__x86_64__) && LINUX_VERSION_CODE < KERNEL_VERSION(6, 2, 0)
#include <linux/mm.h>
#endif

// https://github.com/torvalds/linux/commit/f0907827a8a9152aedac2833ed1b674a7b2a44f2
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 18, 0) || defined(KSU_COMPAT_HAS_OVERFLOW_H)
#include <linux/overflow.h>
#else
// From the commit comment's say
// We can do the things like below
//
//  if (a+b < a)
//     return -EGOAWAY;
//  do_stuff_with(a+b);
//
// Even there have another problem,
//
// While gcc does recognize the 'a+b < a' idiom for testing unsigned add
// overflow, it doesn't do nearly as good for unsigned multiplication
// (there's also no single well-established idiom).
//                                                          --- Rasmus Villemoes (commit author)
//
// But we don't have another choice, Something is better than nothing.
// Let's define check_add_overflow by ourselves when compiler not support this builtin overflow feature

#define check_add_overflow(a, b, d)                                                                                    \
    ({                                                                                                                 \
        typeof(a) _a = (a);                                                                                            \
        typeof(b) _b = (b);                                                                                            \
        typeof(*(d)) _res = _a + _b;                                                                                   \
        *(d) = _res;                                                                                                   \
        _res < _a;                                                                                                     \
    })
#endif

#include "compat/kernel_compat.h"
#include "feature/sulog.h"
#include "infra/event_queue.h"
#include "klog.h" // IWYU pragma: keep
#include "sulog/event.h"

#define KSU_SULOG_MAX_QUEUED 256U
#define KSU_SULOG_MAX_PAYLOAD_LEN 2048U
#define KSU_SULOG_MAX_ARG_STRINGS 0x7FFFFFFF
#define KSU_SULOG_MAX_ARG_CHUNK 256U
#define KSU_SULOG_MAX_FILENAME_LEN 256U

static struct ksu_event_queue sulog_queue;

struct ksu_sulog_pending_event {
    __u16 event_type;
    void *payload;
    __u32 payload_len;
};

struct ksu_sulog_identity {
    __u32 uid;
    __u32 euid;
};

static const char __user *ksu_sulog_get_user_arg_ptr(struct user_arg_ptr argv, int nr)
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

static void ksu_sulog_fill_task_info(struct ksu_sulog_event *event, __u16 event_type, int retval)
{
    event->version = KSU_SULOG_EVENT_VERSION;
    event->event_type = event_type;
    event->retval = retval;
    event->pid = task_pid_nr(current);
    event->tgid = task_tgid_nr(current);
    event->ppid = task_ppid_nr(current);
    event->uid = ksu_get_uid_t(current_uid());
    event->euid = ksu_get_uid_t(current_euid());
    get_task_comm(event->comm, current);
}

static void ksu_sulog_set_identity(struct ksu_sulog_event *event, const struct ksu_sulog_identity *identity)
{
    if (!identity)
        return;

    event->uid = identity->uid;
    event->euid = identity->euid;
}

static __u32 ksu_sulog_copy_empty_string(char *dst)
{
    dst[0] = '\0';
    return 1;
}

static __u32 ksu_sulog_copy_filename(const char __user *filename_user, char *dst, __u32 dst_len)
{
    long ret;

    if (!dst_len)
        return 0;

    if (!filename_user)
        return ksu_sulog_copy_empty_string(dst);

    ret = ksu_strncpy_from_user_nofault(dst, (const void __user *)untagged_addr((unsigned long)filename_user), dst_len);
    if (ret <= 0)
        return ksu_sulog_copy_empty_string(dst);

    if (ret >= dst_len) {
        dst[dst_len - 1] = '\0';
        return dst_len;
    }

    return ret + 1;
}

static __u32 ksu_sulog_flatten_argv(struct user_arg_ptr argv, char *dst, __u32 dst_len)
{
    char arg[KSU_SULOG_MAX_ARG_CHUNK];
    __u32 used = 0;
    int i;

    if (!dst_len)
        return 0;

    if (!argv.ptr.native)
        return ksu_sulog_copy_empty_string(dst);

    for (i = 0; i < KSU_SULOG_MAX_ARG_STRINGS; i++) {
        const char __user *arg_user;
        long copied;
        size_t arg_len;

        if (fatal_signal_pending(current))
            break;

        arg_user = ksu_sulog_get_user_arg_ptr(argv, i);
        if (!arg_user)
            break;
        if (IS_ERR(arg_user))
            return ksu_sulog_copy_empty_string(dst);

        copied = ksu_strncpy_from_user_nofault(arg, (const void __user *)untagged_addr((unsigned long)arg_user),
                                               sizeof(arg));
        if (copied <= 0)
            return ksu_sulog_copy_empty_string(dst);

        if (copied >= sizeof(arg))
            arg[sizeof(arg) - 1] = '\0';

        arg_len = strnlen(arg, sizeof(arg));
        if (!arg_len)
            continue;

        if (used && used < dst_len - 1)
            dst[used++] = ' ';

        if (used >= dst_len - 1)
            break;

        arg_len = min_t(size_t, arg_len, dst_len - used - 1);
        memcpy(dst + used, arg, arg_len);
        used += arg_len;

        if (used >= dst_len - 1)
            break;
    }

    dst[used] = '\0';
    return used + 1;
}

static struct ksu_sulog_pending_event *ksu_sulog_capture_common(__u16 event_type, const char *filename,
                                                                u32 filename_len, const struct user_arg_ptr argv,
                                                                gfp_t gfp)
{
    struct ksu_sulog_pending_event *pending = NULL;
    struct ksu_sulog_event *event;
    void *payload = NULL;
    __u32 payload_len;
    __u32 argv_len;
    __u32 remaining;
    char *filename_buf;
    char *argv_buf;
    bool should_skip_copy = false;

    if (!ksu_sulog_is_enabled())
        return NULL;

    if (event_type == KSU_SULOG_EVENT_IOCTL_GRANT_ROOT) {
        filename_len = 0;
        argv_len = 0;
        should_skip_copy = true;
    }

    // alloc memory
    pending = kzalloc(sizeof(*pending), gfp);
    if (!pending)
        goto out_drop;

    payload = kzalloc(KSU_SULOG_MAX_PAYLOAD_LEN, gfp);
    if (!payload)
        goto out_free_pending;

    // fill task info
    event = payload;
    ksu_sulog_fill_task_info(event, event_type, 0);

    if (should_skip_copy)
        goto skip_copy;

    // start fill filename
    remaining = KSU_SULOG_MAX_PAYLOAD_LEN - sizeof(*event);
    filename_buf = (char *)payload + sizeof(*event);

    size_t copy_len = filename_len;
    if (copy_len > remaining - 1)
        copy_len = remaining - 1;

    memcpy(filename_buf, filename, copy_len);
    filename_buf[copy_len] = '\0';
    filename_len = strlen(filename_buf) + 1;

    // start fill argv
    remaining -= filename_len;
    argv_buf = filename_buf + filename_len;

    argv_len = ksu_sulog_flatten_argv(argv, argv_buf, remaining);
    if (!argv_len)
        goto out_free_payload;

skip_copy:
    // put event information
    event->filename_len = filename_len;
    event->argv_len = argv_len;

    if (check_add_overflow((__u32)sizeof(*event), filename_len, &payload_len) ||
        check_add_overflow(payload_len, argv_len, &payload_len))
        goto out_free_payload;

    pending->event_type = event_type;
    pending->payload = payload;
    pending->payload_len = payload_len;
    return pending;

out_free_payload:
    kfree(payload);
out_free_pending:
    kfree(pending);
out_drop:
    ksu_event_queue_drop(&sulog_queue);
    return NULL;
}

static struct user_arg_ptr ksu_sulog_user_argv(const char __user *const __user *argv_user)
{
    struct user_arg_ptr argv;

#ifdef CONFIG_COMPAT
    if (unlikely(in_compat_syscall())) {
        argv.is_compat = true;
        argv.ptr.compat = (const compat_uptr_t __user *)argv_user;
        return argv;
    }

    argv.is_compat = false;
#endif
    argv.ptr.native = argv_user;
    return argv;
}

#ifdef KSU_TP_HOOK
// Tracepoint Syscall Redirect hook

static inline struct ksu_sulog_pending_event *ksu_sulog_capture_tracepoint(__u16 event_type,
                                                                           const char __user *filename_user,
                                                                           const char __user *const __user *argv_user,
                                                                           gfp_t gfp)
{
    u32 filename_len;
    char filename_buf[KSU_SULOG_MAX_FILENAME_LEN] = {};

    // fast path, directly exit when sulog disable
    if (!ksu_sulog_is_enabled())
        return NULL;

    // copy filename, prepare use argv
    filename_len = ksu_sulog_copy_filename(filename_user, filename_buf, KSU_SULOG_MAX_FILENAME_LEN);
    struct user_arg_ptr argv = ksu_sulog_user_argv(argv_user);

    // submit to common
    return ksu_sulog_capture_common(event_type, filename_buf, filename_len, argv, gfp);
}

struct ksu_sulog_pending_event *ksu_sulog_capture_root_execve_tracepoint(const char __user *filename_user,
                                                                         const char __user *const __user *argv_user,
                                                                         gfp_t gfp)
{
    return ksu_sulog_capture_tracepoint(KSU_SULOG_EVENT_ROOT_EXECVE, filename_user, argv_user, gfp);
}

struct ksu_sulog_pending_event *ksu_sulog_capture_sucompat_tracepoint(const char __user *filename_user,
                                                                      const char __user *const __user *argv_user,
                                                                      gfp_t gfp)
{
    return ksu_sulog_capture_tracepoint(KSU_SULOG_EVENT_SUCOMPAT, filename_user, argv_user, gfp);
}
#else
// Manual hook / SuSFS Inline Hook
struct ksu_sulog_pending_event *ksu_sulog_capture_root_execve_manual(const char *filename,
                                                                     const struct user_arg_ptr argv, gfp_t gfp)
{
    return ksu_sulog_capture_common(KSU_SULOG_EVENT_ROOT_EXECVE, filename, strlen(filename), argv, gfp);
}

struct ksu_sulog_pending_event *ksu_sulog_capture_sucompat_manual(const char *filename, const struct user_arg_ptr argv,
                                                                  gfp_t gfp)
{
    return ksu_sulog_capture_common(KSU_SULOG_EVENT_SUCOMPAT, filename, strlen(filename), argv, gfp);
}
#endif

static struct ksu_sulog_pending_event *ksu_sulog_capture_grant_root(const struct ksu_sulog_identity *identity,
                                                                    gfp_t gfp)
{
    struct ksu_sulog_pending_event *pending;
    struct ksu_sulog_event *event;

    pending = ksu_sulog_capture_common(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, 0, ksu_sulog_user_argv(NULL), gfp);
    if (!pending)
        return NULL;

    event = pending->payload;
    ksu_sulog_set_identity(event, identity);
    return pending;
}

int __init ksu_sulog_events_init(void)
{
    ksu_event_queue_init(&sulog_queue, KSU_SULOG_MAX_QUEUED, KSU_SULOG_MAX_PAYLOAD_LEN);
    return 0;
}

void __exit ksu_sulog_events_exit(void)
{
    ksu_event_queue_destroy(&sulog_queue);
}

static void ksu_sulog_free_pending(struct ksu_sulog_pending_event *pending)
{
    if (!pending)
        return;
    kfree(pending->payload);
    kfree(pending);
}

void ksu_sulog_emit_pending(struct ksu_sulog_pending_event *pending, int retval, gfp_t gfp)
{
    struct ksu_sulog_event *event;

    if (!pending)
        return;

    event = pending->payload;
    event->retval = retval;
    ksu_event_queue_push(&sulog_queue, pending->event_type, 0, pending->payload, pending->payload_len, gfp);
    ksu_sulog_free_pending(pending);
}

int ksu_sulog_emit_grant_root(int retval, __u32 uid, __u32 euid, gfp_t gfp)
{
    struct ksu_sulog_pending_event *pending;
    struct ksu_sulog_identity identity = {
        .uid = uid,
        .euid = euid,
    };

    pending = ksu_sulog_capture_grant_root(&identity, gfp);
    if (!pending)
        return 0;

    ksu_sulog_emit_pending(pending, retval, gfp);
    return 0;
}

struct ksu_event_queue *ksu_sulog_get_queue(void)
{
    return &sulog_queue;
}
