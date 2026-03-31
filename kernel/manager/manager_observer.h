#ifndef __KSU_H_MANAGER_OBSERVER
#define __KSU_H_MANAGER_OBSERVER

// 6.8- manual hook, disable manager -> ksu_observer killed
#if defined(CONFIG_KSU_DISABLE_MANAGER) || (LINUX_VERSION_CODE < KERNEL_VERSION(6, 8, 0) && !defined(KSU_TP_HOOK))
static inline int ksu_observer_init(void)
{
    return 0;
}

static inline void ksu_observer_exit(void)
{
}
#else
int ksu_observer_init(void);
void ksu_observer_exit(void);
#endif

#endif // __KSU_H_MANAGER_OBSERVER
