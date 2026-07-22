define check_ksu_hook
  ifeq ($$(shell grep -q "$(1)" $(2); echo $$$$?),0)
      $$(info -- $$(REPO_NAME)/manual_hook: $(1) found)
  else
      $$(info -- You lost $(1) hook in your kernel)
      $$(info -- Read: https://resukisu.github.io/guide/manual-integrate.html)
      $$(error You should integrate $$(REPO_NAME) in your kernel. $(3))
  endif
endef

$(eval $(call check_ksu_hook,ksu_handle_setresuid,$(srctree)/kernel/sys.c))
$(eval $(call check_ksu_hook,ksu_handle_sys_read,$(srctree)/fs/read_write.c))
$(eval $(call check_ksu_hook,ksu_handle_input_handle_event,$(srctree)/drivers/input/input.c))
$(eval $(call check_ksu_hook,ksu_handle_execve,$(srctree)/fs/exec.c))
$(eval $(call check_ksu_hook,ksu_handle_faccessat,$(srctree)/fs/open.c))
$(eval $(call check_ksu_hook,ksu_handle_stat,$(srctree)/fs/stat.c))
$(eval $(call check_ksu_hook,ksu_handle_newfstat_ret,$(srctree)/fs/stat.c))
$(eval $(call check_ksu_hook,ksu_handle_fstat64_ret,$(srctree)/fs/stat.c))

# TODO: Handle backport
ifeq ($(shell test \( $(VERSION) -lt 3 -o \( $(VERSION) -eq 3 -a $(PATCHLEVEL) -le 11 \) \) && echo y),y)
  # https://github.com/torvalds/linux/commit/15d94b82565ebfb0cf27830b96e6cf5ed2d12a9a
  # when 3.11-, it maybe in kernel/sys.c
  $(eval $(call check_ksu_hook,ksu_handle_sys_reboot,$(srctree)/kernel/sys.c))
else
  # when 3.12+, it is in kernel/reboot.c
  $(eval $(call check_ksu_hook,ksu_handle_sys_reboot,$(srctree)/kernel/reboot.c))
endif
