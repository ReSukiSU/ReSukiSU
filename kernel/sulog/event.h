#ifndef __KSU_H_SULOG_EVENT
#define __KSU_H_SULOG_EVENT

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 14, 0)
#include <linux/compiler_types.h>
#endif
#include <linux/gfp.h>
#include <linux/types.h>
#include "uapi/sulog.h" // IWYU pragma: keep
#include "runtime/ksud.h" // declare user_arg_ptr

struct ksu_event_queue;
struct ksu_sulog_pending_event;

int ksu_sulog_events_init(void);
void ksu_sulog_events_exit(void);

#ifdef KSU_TP_HOOK
struct ksu_sulog_pending_event *ksu_sulog_capture_root_execve_tracepoint(const char __user *filename_user,
                                                                         const char __user *const __user *argv_user,
                                                                         gfp_t gfp);
struct ksu_sulog_pending_event *ksu_sulog_capture_sucompat_tracepoint(const char __user *filename_user,
                                                                      const char __user *const __user *argv_user,
                                                                      gfp_t gfp);
#else
struct ksu_sulog_pending_event *ksu_sulog_capture_root_execve_manual(const char *filename,
                                                                     const struct user_arg_ptr argv, gfp_t gfp);
struct ksu_sulog_pending_event *ksu_sulog_capture_sucompat_manual(const char *filename, const struct user_arg_ptr argv,
                                                                  gfp_t gfp);
#endif

void ksu_sulog_emit_pending(struct ksu_sulog_pending_event *pending, int retval, gfp_t gfp);
int ksu_sulog_emit_grant_root(int retval, __u32 uid, __u32 euid, gfp_t gfp);

struct ksu_event_queue *ksu_sulog_get_queue(void);

#endif
