#ifndef __KSU_H_SUPERCALL_INTERNAL
#define __KSU_H_SUPERCALL_INTERNAL

#include <linux/fs.h>
#include <linux/types.h>
#include <linux/uaccess.h>

#include "supercall/supercall.h"

bool only_manager(void);
bool only_root(void);
bool manager_or_root(void);
bool always_allow(void);
bool allowed_for_su(void);

long ksu_supercall_handle_ioctl(const struct file *filp, unsigned int cmd, void __user *argp);
void ksu_supercall_dump_commands(void);
void ksu_supercall_cleanup_state(void);

// sys_reboot extensions
#define CHANGE_MANAGER_UID 10006 // change ksu manager appid
#define CHANGE_KSUVER 10011 // change ksu version
#define CHANGE_SPOOF_UNAME 10012 // spoof uname
#define CHANGE_KSUFLAGS 10013 // change ksuflags, do the bit calc on your own, 0 + 1 + 2 + 4 + 8 blah

#endif // __KSU_H_SUPERCALL_INTERNAL
