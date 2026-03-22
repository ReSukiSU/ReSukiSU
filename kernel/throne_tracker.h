#ifndef __KSU_H_THRONE_TRACKER
#define __KSU_H_THRONE_TRACKER
#include <linux/dcache.h>

void ksu_throne_tracker_init(void);

void ksu_throne_tracker_exit(void);

void track_throne(bool prune_only, bool force_search_manager);

void ksu_handle_rename(struct dentry *old_dentry, struct dentry *new_dentry);

#endif
