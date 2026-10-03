hostprogs += tools/code_generation/syscall_no/syscall_no
hostprogs-y += tools/code_generation/syscall_no/syscall_no

# `obj` is absolute with some external-module/Kbuild combinations and
# relative with others. Normalize it once so an absolute output directory is
# never appended to `objtree` a second time.
KSU_OBJ := $(if $(filter /%,$(obj)),$(obj),$(objtree)/$(obj))

KSU_SYSCALL_NO_H := $(KSU_OBJ)/compat/syscall_no.h
# Kbuild prefixes target names with the external-module output directory.
# Keep this target relative even when `obj` is already absolute.
KSU_SYSCALL_NO_TARGET := $(obj)/compat/syscall_no.h
KSU_SYSCALL_TOOL := $(obj)/tools/code_generation/syscall_no/syscall_no
ccflags-y += -I$(KSU_OBJ)

ifeq ($(ARCH),arm64)
KSU_SYSCALL_FLAGS := $(if $(CONFIG_COMPAT),--arm64-compat,--arm64)
endif

ifeq ($(ARCH),arm64)
ifneq ($(shell test $(VERSION) -gt 6 -o \( $(VERSION) -eq 6 -a $(PATCHLEVEL) -ge 11 \) || grep -q "__NR_compat32_syscalls" $(srctree)/arch/arm64/kernel/sys32.c 2>/dev/null; echo $$?),0)
ifeq ($(CONFIG_COMPAT),y)
KSU_UNISTD32_ARG := $(srctree)/arch/arm64/include/asm/unistd32.h
endif
endif
endif

$(KSU_SYSCALL_NO_TARGET): $(KSU_SYSCALL_TOOL) $(KSU_UNISTD32_ARG)
	$(call cmd,ksu_syscall_no)

quiet_cmd_ksu_syscall_no = GEN     $@
      cmd_ksu_syscall_no = mkdir -p $(dir $(KSU_SYSCALL_NO_H)); $< $(KSU_SYSCALL_NO_H) $(KSU_SYSCALL_FLAGS) $(KSU_UNISTD32_ARG)

targets += compat/syscall_no.h
always-y += compat/syscall_no.h

$(kernelsu-objs): $(KSU_SYSCALL_NO_TARGET)
$(addprefix $(obj)/,$(kernelsu-objs)): $(KSU_SYSCALL_NO_TARGET)
