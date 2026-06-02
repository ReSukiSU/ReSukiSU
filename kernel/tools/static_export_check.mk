define check_symbol_export
  $(eval _ARG1 := $(strip $(1)))
  $(eval _ARG2 := $(strip $(2)))
  $(eval _ARG3 := $(strip $(3)))
  $(eval _ARG4 := $(strip $(4)))

  ifneq ($$(shell grep -q -F "$$(_ARG1)" $$(_ARG2); echo $$$$?),0)
      $$(info -- $$(REPO_NAME)/symbol_export: export $$(_ARG3) found)
  else
      $$(info -- You disabled CONFIG_KALLSYMS_ALL and haven't export $$(_ARG3) in your kernel's $$(notdir $$(_ARG2)) file)
      $$(info -- Read: https://resukisu.github.io/guide/manual-integrate.html#static-var-export)
      $$(error You should integrate $$(REPO_NAME) in your kernel. $$(_ARG4))
  endif
endef

# Reason: selinux_hide
$(eval $(call check_symbol_export, static ssize_t (*write_op[]), $(srctree)/security/selinux/selinuxfs.c, write_op))

# Reason: selinux_hide
$(eval $(call check_symbol_export, static const struct file_operations sel_handle_status_ops, $(srctree)/security/selinux/selinuxfs.c, sel_handle_status_ops))

# if no selinux_state, we should check selinux_status_page, selinux_status_lock, sel_mutex and policy_rwlock has been NOT static.
ifneq ($(shell grep -q "struct selinux_state " $(srctree)/security/selinux/include/security.h; echo $$?),0)
    # Reason: selinux_hide
    $(eval $(call check_symbol_export, static struct page *selinux_status_page, $(srctree)/security/selinux/ss/status.c, selinux_status_page))
    $(eval $(call check_symbol_export, static DEFINE_MUTEX(selinux_status_lock), $(srctree)/security/selinux/ss/status.c, selinux_status_lock))

    # Reason: safe policydb patch/dup
    $(eval $(call check_symbol_export, static DEFINE_MUTEX(sel_mutex), $(srctree)/security/selinux/selinuxfs.c, sel_mutex))
    $(eval $(call check_symbol_export, static DEFINE_RWLOCK(policy_rwlock), $(srctree)/security/selinux/ss/services.c, policy_rwlock))
endif

# if kernel < 4.2, we should check selinux_ops has NOT been static.
ifeq ($(shell test $(VERSION) -lt 4 -o \( $(VERSION) -eq 4 -a $(PATCHLEVEL) -lt 2 \) && echo y),y)
    # Reason: We need LSM hooks
    $(eval $(call check_symbol_export, static struct security_operations selinux_ops, $(srctree)/security/selinux/hooks.c, selinux_ops))
endif

# if kernel >= 6.6, we should check selinux_ops has NOT been static.
ifeq ($(shell test $(VERSION) -gt 6 -o \( $(VERSION) -eq 6 -a $(PATCHLEVEL) -ge 6 \) && echo y),y)
    # Reason: selinux_hide
    $(eval $(call check_symbol_export, static void security_dump_masked_av, $(srctree)/security/selinux/ss/services.c, security_dump_masked_av func))
    $(eval $(call check_symbol_export, static void context_struct_compute_av, $(srctree)/security/selinux/ss/services.c, context_struct_compute_av func))
endif