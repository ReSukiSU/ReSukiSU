#ifndef __KSU_H_THRONE_TRACKER
#define __KSU_H_THRONE_TRACKER

#ifdef CONFIG_KSU_DISABLE_MANAGER
static inline void ksu_throne_tracker_init(void)
{
}

static inline void ksu_throne_tracker_exit(void)
{
}

static inline void track_throne(bool prune_only, bool force_search_manager, bool from_renameat)
{
}
#else
void ksu_throne_tracker_init(void);
void ksu_throne_tracker_exit(void);
void track_throne(bool prune_only, bool force_search_manager, bool from_renameat);
#endif

#endif
