#ifndef __KSU_H_KSU_MANAGER
#define __KSU_H_KSU_MANAGER

#include <linux/cred.h>
#include <linux/types.h>
#include <linux/version.h>
#include "allowlist.h"

#define PER_USER_RANGE 100000
#define KSU_INVALID_APPID -1
extern u16 ksu_last_manager_appid;

static inline void ksu_mark_manager(u32 uid)
{
    ksu_last_manager_appid = uid % PER_USER_RANGE;
}

extern bool is_manager(void);
bool ksu_is_manager_appid(u16 appid);
extern bool ksu_is_manager_uid(u32 uid);
extern void ksu_register_manager(u32 uid, u8 signature_index);
extern void ksu_unregister_manager(u32 uid);
extern void ksu_unregister_manager_by_signature_index(u8 signature_index);
extern int ksu_get_manager_signature_index_by_appid(u16 appid);
extern bool ksu_has_manager(void);

// in 6.8- manual hook, we use LSM rename hook
#if LINUX_VERSION_CODE > KERNEL_VERSION(6, 8, 0) || defined(KSU_TP_HOOK)
int ksu_observer_init(void);
void ksu_observer_exit(void);
#endif

#endif