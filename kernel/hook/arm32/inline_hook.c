/* SPDX-License-Identifier: GPL-2.0-only */

#ifdef __arm__

#include "hook/inline_hook_internal.h"

#include <linux/errno.h>
#include <linux/gfp.h>
#include <linux/mm.h>
#include <linux/numa.h>
#include <linux/string.h>
#include <linux/vmalloc.h>
#include <linux/version.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 10, 0)
#include <linux/execmem.h>
#endif

#include <asm/cacheflush.h>
#include <asm/pgtable.h>
#include <asm/sections.h>

#include "hook/patch_memory.h"

#define KSU_ARM32_PATCH_SIZE 8
#define KSU_ARM32_ENTRY_SIZE 32

#ifdef CONFIG_XIP_KERNEL
#undef MODULES_VADDR
#define MODULES_VADDR (((unsigned long)_exiprom + ~PMD_MASK) & PMD_MASK)
#endif

extern void ksu_inline_hook_arm32_entry_with_after(void);
extern void ksu_inline_hook_arm32_entry_with_before(void);
extern void ksu_inline_hook_arm32_entry_with_before_and_after(void);

void *ksu_inline_hook_arch_normalize_target(void *target)
{
    return (void *)((unsigned long)target & ~1UL);
}

size_t ksu_inline_hook_arch_patch_size(void)
{
#ifdef CONFIG_THUMB2_KERNEL
    return 0;
#else
    return KSU_ARM32_PATCH_SIZE;
#endif
}

int ksu_inline_hook_arch_make_branch(void *to, u8 *patch, size_t patch_size)
{
    u32 *insn = (u32 *)patch;
    u32 addr = (u32)to;

    if (patch_size != KSU_ARM32_PATCH_SIZE)
        return -EINVAL;

    insn[0] = 0xe51ff004;
    memcpy(patch + sizeof(u32), &addr, sizeof(addr));
    return 0;
}

unsigned long ksu_inline_hook_arch_get_ret(const struct pt_regs *regs)
{
    return regs->ARM_r0;
}

void ksu_inline_hook_arch_setup_regs(struct pt_regs *regs, unsigned long *arg_regs)
{
    regs->ARM_r0 = arg_regs[0];
    regs->ARM_r1 = arg_regs[1];
    regs->ARM_r2 = arg_regs[2];
    regs->ARM_r3 = arg_regs[3];
    regs->ARM_ORIG_r0 = arg_regs[0];
    regs->ARM_sp = arg_regs[4];
}

void ksu_inline_hook_arch_update_args(const struct pt_regs *regs, unsigned long *arg_regs)
{
    arg_regs[0] = regs->ARM_r0;
    arg_regs[1] = regs->ARM_r1;
    arg_regs[2] = regs->ARM_r2;
    arg_regs[3] = regs->ARM_r3;
}

void ksu_inline_hook_arch_set_ret(struct pt_regs *regs, unsigned long ret)
{
    regs->ARM_r0 = ret;
}

static void *ksu_inline_hook_clone_code_alloc(size_t size)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0)
    gfp_t gfp_mask = GFP_KERNEL;
    void *p;

    if (IS_ENABLED(CONFIG_ARM_MODULE_PLTS))
        gfp_mask |= __GFP_NOWARN;

    p = __vmalloc_node_range(size, 1, MODULES_VADDR, MODULES_END, gfp_mask, PAGE_KERNEL_EXEC, 0, NUMA_NO_NODE,
                             __builtin_return_address(0));
    if (!IS_ENABLED(CONFIG_ARM_MODULE_PLTS) || p)
        return p;

    return __vmalloc_node_range(size, 1, VMALLOC_START, VMALLOC_END, GFP_KERNEL, PAGE_KERNEL_EXEC, 0, NUMA_NO_NODE,
                                __builtin_return_address(0));
#else
    return execmem_alloc(EXECMEM_DEFAULT, size);
#endif
}

static void ksu_inline_make_entry_stub(struct ksu_inline_hook *hook, void *buf)
{
    u32 *insn = buf;
    u32 hook_addr = (u32)hook;
    u32 clone_addr = (u32)hook->clone;
    u32 entry_addr;

    memset(buf, 0, KSU_ARM32_ENTRY_SIZE);

    if (hook->before && hook->after)
        entry_addr = (u32)ksu_inline_hook_arm32_entry_with_before_and_after;
    else if (hook->before)
        entry_addr = (u32)ksu_inline_hook_arm32_entry_with_before;
    else
        entry_addr = (u32)ksu_inline_hook_arm32_entry_with_after;

    if (!hook->before) {
        insn[0] = 0xe52de004;
        insn[1] = 0xe59fc004;
        insn[2] = 0xe59fe004;
        insn[3] = 0xe59ff004;
        memcpy(&insn[4], &hook_addr, sizeof(hook_addr));
        memcpy(&insn[5], &clone_addr, sizeof(clone_addr));
        memcpy(&insn[6], &entry_addr, sizeof(entry_addr));
        insn[7] = 0xe1a00000;
        return;
    }

    insn[0] = 0xe59fc004;
    insn[1] = 0xe59ff004;
    insn[2] = 0xe1a00000;
    memcpy(&insn[3], &hook_addr, sizeof(hook_addr));
    memcpy(&insn[4], &entry_addr, sizeof(entry_addr));
}

int ksu_inline_hook_arch_prepare(struct ksu_inline_hook *hook, u8 *patch, size_t patch_size)
{
#ifdef CONFIG_THUMB2_KERNEL
    return -EOPNOTSUPP;
#else
    void *code;
    void *entry;
    u8 *clone;
    int ret;

    if (patch_size != KSU_ARM32_PATCH_SIZE)
        return -EINVAL;

    code = ksu_inline_hook_clone_code_alloc(PAGE_SIZE);
    if (!code)
        return -ENOMEM;

    hook->keep_storage = true;
    hook->code = code;
    hook->code_size = PAGE_SIZE;
    hook->clone = code;

    clone = code;
    memcpy(clone, hook->orig, patch_size);

    ret = ksu_inline_hook_arch_make_branch((u8 *)hook->target + patch_size, clone + patch_size, patch_size);
    if (ret)
        goto err_free;

    entry = clone + patch_size * 2;
    ksu_inline_make_entry_stub(hook, entry);
    flush_icache_range((unsigned long)code, (unsigned long)code + hook->code_size);

    ret = ksu_inline_hook_arch_make_branch(entry, patch, patch_size);
    if (ret)
        goto err_free;

    return 0;

err_free:
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0)
    vfree(code);
#else
    execmem_free(code);
#endif
    hook->clone = NULL;
    hook->code = NULL;
    hook->code_size = 0;
    return ret;
#endif
}

void ksu_inline_hook_arch_release(struct ksu_inline_hook *hook)
{
    if (!hook->active && hook->code) {
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0)
        vfree(hook->code);
#else
        execmem_free(hook->code);
#endif
        hook->code = NULL;
        hook->code_size = 0;
        hook->clone = NULL;
    }

    hook->slot = KSU_INLINE_INVALID_SLOT;
}

#endif
