#ifndef __KSU_H_THRONE_TRACKER
#define __KSU_H_THRONE_TRACKER

#define TRACK_THRONE_PRUNE_ONLY (1 << 0)
#define TRACK_THRONE_FORCE_SEARCH_MGR (1 << 1)
#define TRACK_THRONE_FROM_RENAMEAT (1 << 2)

#ifdef CONFIG_KSU_DISABLE_MANAGER
static inline void ksu_throne_tracker_init(void)
{
}

static inline void ksu_throne_tracker_exit(void)
{
}

static inline void track_throne(unsigned int flags)
{
}
#else
void ksu_throne_tracker_init(void);
void ksu_throne_tracker_exit(void);
void track_throne(unsigned int flags);
#endif

#endif
