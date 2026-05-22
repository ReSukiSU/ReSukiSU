# susfs selinux_hide feature
# old susfs won't have this manual hook
# and nongki 4.14- can't use simonpunk's hook for that
# check it, when there doesn't have, fallback to auto hook
ifeq ($(shell grep -q "ksu_selinux_hide_running" $(srctree)/security/selinux/hooks.c; echo $$?),0)
$(info -- $(REPO_NAME)/susfs_feature_check: selinux_hide manual hook found)
ccflags-y += -DKSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE
endif
