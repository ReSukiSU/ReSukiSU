define check_ksu_hook
    ifeq ($$(shell grep -q "$(1)" $(2); echo $$$$?),0)
        $$(info -- $$(REPO_NAME)/susfs_inline: $(1) found)
    else
        $$(info -- You lost $(1) hook in your kernel)
        $$(info -- Please submit issue to your SUSFS patches author.)
        $$(error You should integrate $$(REPO_NAME) in your kernel. $(3))
    endif
endef

define check_ksu_hook_incompatible
    ifeq ($$(shell grep -q "$(1)" $(2); echo $$$$?),0)
        $$(info -- $(1) is incompatible hook)
        $$(info -- Please submit issue to your SUSFS patches author.)
        $$(error You should integrate $$(REPO_NAME) in your kernel correctly.)
    endif
endef

$(eval $(call check_ksu_hook_incompatible,ksu_vfs_read_hook,$(srctree)/fs/read_write.c))

$(eval $(call check_ksu_hook,ksu_handle_setresuid,$(srctree)/kernel/sys.c))
$(eval $(call check_ksu_hook,ksu_handle_execveat,$(srctree)/fs/exec.c))
$(eval $(call check_ksu_hook,ksu_handle_faccessat,$(srctree)/fs/open.c))
$(eval $(call check_ksu_hook,ksu_handle_stat,$(srctree)/fs/stat.c))
$(eval $(call check_ksu_hook,ksu_handle_sys_reboot,$(srctree)/kernel/reboot.c))
$(eval $(call check_ksu_hook,ksu_handle_input_handle_event,$(srctree)/drivers/input/input.c))
