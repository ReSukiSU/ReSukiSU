#ifndef __KSU_H_MANAGER_IDENTITY
#define __KSU_H_MANAGER_IDENTITY

#include <linux/cred.h>
#include <linux/types.h>

#ifdef CONFIG_KSU_DISABLE_MANAGER
static inline void ksu_mark_manager(u32 uid)
{
}

static inline bool is_manager(void)
{
    return current_uid().val == 0;
}

static inline bool ksu_is_manager_appid(u16 appid)
{
    return appid == 0;
}

static inline bool ksu_is_manager_uid(u32 uid)
{
    return uid == 0;
}

static inline bool ksu_register_manager(u32 uid, u8 signature_index)
{
    return true;
}

static inline bool ksu_unregister_manager(u32 uid)
{
    return true;
}

static inline void ksu_unregister_manager_by_signature_index(u8 signature_index)
{
}

static inline int ksu_get_manager_signature_index_by_appid(u16 appid)
{
    return -EOPNOTSUPP;
}

static inline bool ksu_has_manager(void)
{
    return true;
}
#else
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
#endif

#endif
