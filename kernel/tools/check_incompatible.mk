define check_ksu_hook_incompatible
  ifeq ($$(shell grep -q "$(1)" $(2); echo $$$$?),0)
      $$(info -- $(1) is incompatible hook)
      $$(info -- Read: https://resukisu.github.io/guide/manual-integrate.html)
      $$(error You should integrate $$(REPO_NAME) in your kernel correctly.)
  endif
endef

$(eval $(call check_ksu_hook_incompatible,ksu_vfs_read_hook,$(srctree)/fs/read_write.c))
$(eval $(call check_ksu_hook_incompatible,is_ksu_transition,$(srctree)/security/selinux/hooks.c))
# we no need this hook, because we can directly replace selinux_ops to LSM hook in UL
$(eval $(call check_ksu_hook_incompatible,ksu_handle_rename,$(srctree)/security/security.c))

ifeq ($(CONFIG_KSU_SUSFS),y)
  # Due to https://gitlab.com/simonpunk/susfs4ksu/-/commit/00be2d47171a0d8f0edb73ca1d5b45340bd72239
  # The commit has using static_key to replace the bool check.
  # So we need to add these old hook check to make sure the old hooks are changed to new hooks, 
  $(eval $(call check_ksu_hook_incompatible,ksu_input_hook,$(srctree)/drivers/input/input.c))
  $(eval $(call check_ksu_hook_incompatible,ksu_execveat_hook,$(srctree)/fs/exec.c))
  $(eval $(call check_ksu_hook_incompatible,ksu_init_rc_hook,$(srctree)/fs/read_write.c))
  $(eval $(call check_ksu_hook_incompatible,ksu_init_rc_hook,$(srctree)/fs/stat.c))
endif