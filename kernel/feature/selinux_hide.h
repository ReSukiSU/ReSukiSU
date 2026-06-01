#ifndef __KSU_H_SELINUX_HIDE
#define __KSU_H_SELINUX_HIDE
#include <linux/types.h>
#include <linux/version.h>
#include <linux/sched.h>
#include <linux/jump_label.h>

void ksu_selinux_hide_init();
void ksu_selinux_hide_exit();
void ksu_selinux_hide_drop_backup_if_unused();

/* Exported symbols for external kernel integration */
extern bool ksu_selinux_hide_enabled;
extern bool ksu_selinux_hide_running;
extern bool initialize_fake_status;
extern DECLARE_STATIC_KEY_FALSE(fake_status_initialize_key);

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 6, 0)
struct selinux_policy;
extern struct selinux_policy *fake_status;
#else
struct selinux_state;
extern struct selinux_state *fake_status;
#endif

// https://github.com/torvalds/linux/commit/b21507e272627c434e8dd74e8d51fd8245281b59
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0) || defined(KSU_COMPAT_SETPROCATTR_USE_NEW_PROTOTYPE)
typedef int (*setprocattr_fn)(const char *name, void *value, size_t size);

int ksu_handle_selinux_setprocattr(const char *name, void *value, size_t size);
#else
typedef int (*setprocattr_fn)(struct task_struct *p, char *name, void *value, size_t size);

int ksu_handle_selinux_setprocattr(struct task_struct *p, char *name, void *value, size_t size);
#endif

#endif
