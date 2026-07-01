ifneq ($(shell grep -q "ksu_handle_setresuid" $(srctree)/kernel/sys.c 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/auto_hook: using auto hook for setuid hook)
ccflags-y += -DKSU_HOOK_AUTO_SETUID_HOOK
LSM_AUTO_SETUID_HOOK := 1
endif

ifneq ($(shell grep -q "ksu_handle_sys_read" $(srctree)/fs/read_write.c 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/auto_hook: using auto hook for initrc hook)
ccflags-y += -DKSU_HOOK_AUTO_INITRC_HOOK
LSM_AUTO_INIT_RC_HOOK := 1
endif

ifneq ($(shell grep -q "ksu_handle_input_handle_event" $(srctree)/drivers/input/input.c 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/auto_hook: using auto hook for input hook)
ccflags-y += -DKSU_HOOK_AUTO_INPUT_HOOK
endif

ifneq ($(shell grep -q "ksu_handle_sys_reboot" $(srctree)/kernel/reboot.c 2>/dev/null; echo $$?),0)
    ifeq ($(shell test \( $(VERSION) -lt 3 -o \( $(VERSION) -eq 3 -a $(PATCHLEVEL) -le 11 \) \) && echo y),y)
        ifneq ($(shell grep -q "ksu_handle_sys_reboot" $(srctree)/kernel/sys.c 2>/dev/null; echo $$?),0)
            $(info -- $(REPO_NAME)/auto_hook: using auto hook for reboot hook)
            ccflags-y += -DKSU_HOOK_AUTO_REBOOT_HOOK
        endif
    else
        $(info -- $(REPO_NAME)/auto_hook: using auto hook for reboot hook)
        ccflags-y += -DKSU_HOOK_AUTO_REBOOT_HOOK
    endif
endif

ifneq ($(shell grep -q "ksu_handle_execve" $(srctree)/fs/exec.c 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/auto_hook: using auto hook for execve hook)
ccflags-y += -DKSU_HOOK_AUTO_EXECVE_HOOK
endif

ifneq ($(shell grep -q "ksu_handle_faccessat" $(srctree)/fs/open.c 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/auto_hook: using auto hook for faccessat hook)
ccflags-y += -DKSU_HOOK_AUTO_FACCESSAT_HOOK
endif

ifneq ($(shell grep -q "ksu_handle_stat" $(srctree)/fs/stat.c 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/auto_hook: using auto hook for stat hook)
ccflags-y += -DKSU_HOOK_AUTO_STAT_HOOK
endif

ifneq ($(shell grep -q "ksu_handle_newfstat_ret" $(srctree)/fs/stat.c 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/auto_hook: using auto hook for newfstat hook)
ccflags-y += -DKSU_HOOK_AUTO_NEWFSTAT_HOOK
endif

ifneq ($(shell grep -q "ksu_handle_fstat64_ret" $(srctree)/fs/stat.c 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/auto_hook: using auto hook for fstat64 hook)
ccflags-y += -DKSU_HOOK_AUTO_FSTAT64_HOOK
endif

ifeq ($(shell test \( $(VERSION) -gt 6 -o \( $(VERSION) -eq 6 -a $(PATCHLEVEL) -ge 8 \) \) && echo y),y)
    ifeq ($(LSM_AUTO_SETUID_HOOK),1)
        $(info -- You can't use LSM hooks for kernel version >=6.8)
        $(info -- You MUST hook setresuid manually)
        $(info -- Read: https://resukisu.github.io/guide/manual-integrate.html)
    endif

    ifeq ($(LSM_AUTO_INIT_RC_HOOK),1)
        $(info -- You can't use LSM hooks for kernel version >=6.8)
        $(info -- You MUST hook sys_read manually.)
        $(info -- Read: https://resukisu.github.io/guide/manual-integrate.html)
        $(error You can't use LSM hooks when kernel version >= 6.8)
    endif
    $(error You can't use LSM hooks when kernel version >= 6.8)
endif