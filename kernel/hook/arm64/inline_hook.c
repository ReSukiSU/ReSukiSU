/* SPDX-License-Identifier: GPL-2.0-only */

#ifdef __aarch64__

#include "hook/inline_hook_internal.h"

#include <linux/errno.h>
#include <linux/kallsyms.h>
#include <linux/kernel.h>
#include <linux/moduleloader.h>
#include <linux/numa.h>
#include <linux/version.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 0, 0)
#include <linux/kasan.h>
#endif
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 12, 0) || defined(KSU_COMPAT_HAVE_SET_MEMORY_HEADER)
#include <asm/set_memory.h>
#else
#include <asm/cacheflush.h>
#endif
#include <linux/sizes.h>
#include <linux/string.h>
#include <linux/vmalloc.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 10, 0)
#include <linux/execmem.h>
#endif

#include <asm/memory.h>
#include <asm/module.h>
#include <asm/sections.h>
#include <asm/thread_info.h>

#include <linux/gfp.h>
#include <linux/mm.h>
#include "feature/sucompat.h"
#include "hook/patch_memory.h"
#include "infra/symbol_resolver.h"
#include "compat/kernel_compat.h"

#ifdef CONFIG_ARM64_BTI_KERNEL
#define KSU_INLINE_PATCH_SIZE 20
#else
#define KSU_INLINE_PATCH_SIZE 16
#endif
#define KSU_INLINE_ENTRY_SIZE 96
#define KSU_INLINE_VENEER_SIZE 32
#define KSU_INLINE_BRANCH_RANGE SZ_128M
#define KSU_AARCH64_LDR_X16_8 0x58000050
#define KSU_AARCH64_LDR_X16_12 0x58000070
#define KSU_AARCH64_LDR_X16_16 0x58000090
#define KSU_AARCH64_LDR_X17_20 0x580000b1
#define KSU_AARCH64_BR_X16 0xd61f0200
#define KSU_AARCH64_BR_X17 0xd61f0220
#define KSU_AARCH64_BLR_X16 0xd63f0200
#define KSU_AARCH64_RET 0xd65f03c0
#define KSU_AARCH64_MRS_X16_SP_EL0 0xd5384110
#define KSU_AARCH64_LDR_X16_X16 0xf9400210
#define KSU_AARCH64_MOV_X16_SP 0x910003f0
#define KSU_AARCH64_AND_X16_X16_X17 0x8a110210
#define KSU_AARCH64_TBNZ_X16_TIF_PROC_NON_PRIVILEGE                                                                    \
    (0x37000010 | ((TIF_PROC_NON_PRIVILEGE & 0x20) << 26) | ((TIF_PROC_NON_PRIVILEGE & 0x1f) << 19))
#define KSU_AARCH64_STP_X16_X30_PRE 0xa9bf7bf0
#define KSU_AARCH64_LDP_X16_X30_POST 0xa8c17bf0
#define KSU_AARCH64_LDR_X_LITERAL 0x58000000
#define KSU_AARCH64_NOP 0xd503201f
#define KSU_AARCH64_BTI_JC 0xd50324df
#define KSU_AARCH64_B 0x14000000

#if !defined(CONFIG_THREAD_INFO_IN_TASK) &&                                                                            \
    (LINUX_VERSION_CODE < KERNEL_VERSION(4, 5, 0) || defined(KSU_COMPAT_ARM64_THREAD_INFO_BY_SP))
#define KSU_ARM64_THREAD_INFO_BY_SP
#endif

typedef unsigned long (*ksu_inline_arm64_clone_fn_t)(unsigned long, unsigned long, unsigned long, unsigned long,
                                                     unsigned long, unsigned long, unsigned long, unsigned long);

static unsigned long __nocfi noinline ksu_inline_hook_arm64_entry_dispatch(unsigned long arg0, unsigned long arg1,
                                                                           unsigned long arg2, unsigned long arg3,
                                                                           unsigned long arg4, unsigned long arg5,
                                                                           unsigned long arg6, unsigned long arg7)
{
    struct ksu_inline_hook *hook;
    unsigned long args[] = { arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, 0 };
    ksu_inline_arm64_clone_fn_t clone;
    unsigned long ret;

    asm volatile("mov %0, x16" : "=r"(hook));

    if (!hook)
        return 0;

    clone = (ksu_inline_arm64_clone_fn_t)ksu_inline_hook_before(hook, args);
    ret = clone(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);

    return ksu_inline_hook_after(hook, ret, args);
}

static void ksu_inline_dump_target(const char *reason, struct ksu_inline_hook *hook, unsigned long size)
{
    unsigned long dump_size;
    unsigned long i;

    pr_err("inline_hook: %s target=%px (%pS) size=%lu patch_size=%zu\n", reason, hook->target, hook->target, size,
           hook->patch_size);

    dump_size = min_t(unsigned long, size ?: hook->patch_size, 32);
    dump_size &= ~(sizeof(u32) - 1);
    for (i = 0; i < dump_size; i += sizeof(u32)) {
        u32 insn;

        memcpy(&insn, (void *)((unsigned long)hook->target + i), sizeof(insn));
        pr_err("inline_hook: target+0x%02lx insn=%08x\n", i, insn);
    }
}

static inline s64 ksu_inline_sign_extend(u64 value, int bits)
{
    return (s64)(value << (64 - bits)) >> (64 - bits);
}

static inline bool ksu_inline_simm_fits(s64 value, int bits)
{
    s64 min = -(1LL << (bits - 1));
    s64 max = (1LL << (bits - 1)) - 1;

    return value >= min && value <= max;
}

static inline bool ksu_inline_is_bti(u32 insn)
{
    return insn == 0xd503245f || insn == 0xd503249f || insn == 0xd50324df;
}

static inline bool ksu_inline_is_reg_branch(u32 insn)
{
    if ((insn & 0xfffffc1f) == 0xd61f0000)
        return true;
    if ((insn & 0xfffffc1f) == 0xd63f0000)
        return true;
    if ((insn & 0xfffffc1f) == 0xd65f0000)
        return true;

    return false;
}

struct ksu_inline_reloc_ctx {
    unsigned long old_base;
    unsigned long new_base;
    unsigned long size;
    unsigned long veneer_cursor;
    unsigned long veneer_end;
};

static inline unsigned long ksu_inline_map_clone_addr(const struct ksu_inline_reloc_ctx *ctx, unsigned long addr)
{
    if (addr >= ctx->old_base && addr < ctx->old_base + ctx->size)
        return ctx->new_base + (addr - ctx->old_base);

    return addr;
}

static inline unsigned long ksu_inline_map_clone_page(const struct ksu_inline_reloc_ctx *ctx, unsigned long addr)
{
    unsigned long old_page = ctx->old_base & PAGE_MASK;
    unsigned long old_end_page = PAGE_ALIGN(ctx->old_base + ctx->size);
    unsigned long new_page = ctx->new_base & PAGE_MASK;

    if (addr >= old_page && addr < old_end_page)
        return new_page + (addr - old_page);

    return addr;
}

static int ksu_inline_encode_branch(u32 opcode, unsigned long from, unsigned long to, u32 *out)
{
    s64 diff = (s64)to - (s64)from;
    s64 imm = diff >> 2;

    if (diff & 0x3)
        return -ERANGE;
    if (!ksu_inline_simm_fits(imm, 26))
        return -ERANGE;

    *out = (opcode & 0xfc000000) | ((u32)imm & 0x03ffffff);
    return 0;
}

static int ksu_inline_emit_branch_veneer(struct ksu_inline_reloc_ctx *ctx, unsigned long from, unsigned long dst,
                                         bool link, u32 *out)
{
    unsigned long veneer = ALIGN(ctx->veneer_cursor, 8);
    u32 *insn = (u32 *)veneer;
    u64 literal = dst;
    int ret;

    if (veneer + KSU_INLINE_VENEER_SIZE > ctx->veneer_end)
        return -ENOSPC;

    if (link) {
        insn[0] = KSU_AARCH64_STP_X16_X30_PRE;
        insn[1] = 0x580000b0; /* ldr x16, #20 -> veneer + 24 */
        insn[2] = KSU_AARCH64_BLR_X16;
        insn[3] = KSU_AARCH64_LDP_X16_X30_POST;
        ret = ksu_inline_encode_branch(0x14000000, veneer + 16, from + sizeof(u32), &insn[4]);
        if (ret)
            return ret;
        memcpy((void *)(veneer + 24), &literal, sizeof(literal));
        ctx->veneer_cursor = veneer + 32;
    } else {
        insn[0] = KSU_AARCH64_STP_X16_X30_PRE;
        insn[1] = 0x580000b0; /* ldr x16, #20 -> veneer + 24 */
        insn[2] = KSU_AARCH64_BLR_X16;
        insn[3] = KSU_AARCH64_LDP_X16_X30_POST;
        insn[4] = KSU_AARCH64_RET;
        insn[5] = KSU_AARCH64_NOP;
        memcpy((void *)(veneer + 24), &literal, sizeof(literal));
        ctx->veneer_cursor = veneer + 32;
    }

    return ksu_inline_encode_branch(link ? 0x94000000 : 0x14000000, from, veneer, out);
}

static int ksu_inline_encode_imm19(u32 insn, unsigned long from, unsigned long to, u32 *out)
{
    s64 diff = (s64)to - (s64)from;
    s64 imm = diff >> 2;

    if (diff & 0x3)
        return -ERANGE;
    if (!ksu_inline_simm_fits(imm, 19))
        return -ERANGE;

    *out = (insn & 0xff00001f) | (((u32)imm & 0x7ffff) << 5);
    return 0;
}

static int ksu_inline_encode_tbz(u32 insn, unsigned long from, unsigned long to, u32 *out)
{
    s64 diff = (s64)to - (s64)from;
    s64 imm = diff >> 2;

    if (diff & 0x3)
        return -ERANGE;
    if (!ksu_inline_simm_fits(imm, 14))
        return -ERANGE;

    *out = (insn & 0xfff8001f) | (((u32)imm & 0x3fff) << 5);
    return 0;
}

static int ksu_inline_encode_ldr_literal(u32 reg, unsigned long from, unsigned long literal, u32 *out)
{
    s64 diff = (s64)literal - (s64)from;
    s64 imm = diff >> 2;

    if (diff & 0x3)
        return -ERANGE;
    if (!ksu_inline_simm_fits(imm, 19))
        return -ERANGE;

    *out = KSU_AARCH64_LDR_X_LITERAL | (((u32)imm & 0x7ffff) << 5) | (reg & 0x1f);
    return 0;
}

void *ksu_inline_hook_arch_normalize_target(void *target)
{
    return target;
}

size_t ksu_inline_hook_arch_patch_size(void)
{
    return KSU_INLINE_PATCH_SIZE;
}

int ksu_inline_hook_arch_make_branch(void *to, u8 *patch, size_t patch_size)
{
    u32 *insn = (u32 *)patch;
    u64 literal = (u64)to;

#ifdef CONFIG_ARM64_BTI_KERNEL
    if (patch_size != KSU_INLINE_PATCH_SIZE)
        return -EINVAL;

    insn[0] = KSU_AARCH64_BTI_JC;
    insn[1] = KSU_AARCH64_LDR_X16_8;
    insn[2] = KSU_AARCH64_BR_X16;
    memcpy(patch + 12, &literal, sizeof(literal));
    return 0;
#else
    if (patch_size != KSU_INLINE_PATCH_SIZE)
        return -EINVAL;

    insn[0] = KSU_AARCH64_LDR_X16_8;
    insn[1] = KSU_AARCH64_BR_X16;
    memcpy(patch + 8, &literal, sizeof(literal));
    return 0;
#endif
}

static int ksu_inline_hook_arch_make_direct_branch(void *from, void *to, u8 *patch, size_t patch_size)
{
    u32 *insn = (u32 *)patch;
    int ret;

    if (patch_size != KSU_INLINE_PATCH_SIZE)
        return -EINVAL;

    memset(patch, 0, patch_size);

#ifdef CONFIG_ARM64_BTI_KERNEL
    insn[0] = KSU_AARCH64_BTI_JC;
    ret = ksu_inline_encode_branch(KSU_AARCH64_B, (unsigned long)from + sizeof(u32), (unsigned long)to, &insn[1]);
#else
    ret = ksu_inline_encode_branch(KSU_AARCH64_B, (unsigned long)from, (unsigned long)to, &insn[0]);
#endif
    if (ret)
        return ret;

    for (; (u8 *)insn < patch + patch_size; insn++) {
        if (!*insn)
            *insn = KSU_AARCH64_NOP;
    }

    return 0;
}

unsigned long ksu_inline_hook_arch_get_ret(const struct pt_regs *regs)
{
    return regs->regs[0];
}

#define KSU_INLINE_HOOK_ARG_REGS_NR 9

void ksu_inline_hook_arch_setup_regs(struct pt_regs *regs, unsigned long *arg_regs)
{
    if (!arg_regs)
        return;

    memcpy(regs->regs, arg_regs, KSU_INLINE_HOOK_ARG_REGS_NR * sizeof(regs->regs[0]));
    regs->orig_x0 = arg_regs[0];
}

void ksu_inline_hook_arch_update_args(const struct pt_regs *regs, unsigned long *arg_regs)
{
    if (!arg_regs)
        return;

    memcpy(arg_regs, regs->regs, KSU_INLINE_HOOK_ARG_REGS_NR * sizeof(arg_regs[0]));
}

void ksu_inline_hook_arch_set_ret(struct pt_regs *regs, unsigned long ret)
{
    regs->regs[0] = ret;
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 5, 0) || defined(KSU_COMPAT_MODULE_ALLOC_BASE_IN_MODULE_C)
// https://github.com/torvalds/linux/commit/e46b7103aef39c3f421f0bff7a10ae5a29cd5cee
static u64 ksu_module_alloc_base;

static u64 ksu_inline_get_module_alloc_base(void)
{
    if (!ksu_module_alloc_base) {
        u64 *addr = (u64 *)find_kernel_symbol_exact("module_alloc_base");

        if (addr)
            ksu_module_alloc_base = READ_ONCE(*addr);
    }

    if (ksu_module_alloc_base >= MODULES_VADDR && ksu_module_alloc_base < MODULES_END)
        return ksu_module_alloc_base;

    pr_warn_once("inline_hook: invalid module_alloc_base=0x%llx, fallback to MODULES_VADDR\n",
                 (unsigned long long)ksu_module_alloc_base);
    return MODULES_VADDR;
}
#else
static inline u64 ksu_inline_get_module_alloc_base(void)
{
    return module_alloc_base;
}
#endif

#ifndef MODULES_VSIZE
#include <linux/sizes.h>

#define MODULES_VSIZE (SZ_2G)
#endif

#ifndef MODULE_ALIGN
#define MODULE_ALIGN PAGE_SIZE
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0) && !defined(KSU_COMPAT_HAVE_EXECMEM_API)
static inline int ksu_inline_kasan_module_alloc(void *p, size_t size, gfp_t flags)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 0, 0)
    return 0;
#else
#ifdef KSU_COMPAT_HAVE_KASAN_ALLOC_MODULE_SHADOW
    return kasan_alloc_module_shadow(p, size, flags);
#else
    return kasan_module_alloc(p, size);
#endif
#endif
}

static inline void *ksu_inline_kasan_reset_tag(void *p)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 0, 0)
    return p;
#else
    return kasan_reset_tag(p);
#endif
}
#endif

static inline bool ksu_inline_branch_in_range(unsigned long from, unsigned long to)
{
    s64 diff = (s64)to - (s64)from;

    return !(diff & 0x3) && ksu_inline_simm_fits(diff >> 2, 26);
}

#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0) && !defined(KSU_COMPAT_HAVE_EXECMEM_API)
static void *ksu_inline_vmalloc_exec_range(size_t size, unsigned long start, unsigned long end, gfp_t gfp_mask)
{
    void *p;

    if (end <= start || end - start < PAGE_ALIGN(size))
        return NULL;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 0, 0) || defined(KSU_COMPAT_HAVE_VMFLAGS_IN_VMALLOC_NODE_RANGE)
    p = __vmalloc_node_range(size, MODULE_ALIGN, start, end, gfp_mask, PAGE_KERNEL_EXEC, 0, NUMA_NO_NODE,
                             __builtin_return_address(0));
#else
    p = __vmalloc_node_range(size, MODULE_ALIGN, start, end, gfp_mask, PAGE_KERNEL_EXEC, NUMA_NO_NODE,
                             __builtin_return_address(0));
#endif

    if (p && ksu_inline_kasan_module_alloc(p, size, gfp_mask) < 0) {
        vfree(p);
        return NULL;
    }

    return ksu_inline_kasan_reset_tag(p);
}
#endif

static inline void *ksu_inline_hook_code_alloc(void *target, size_t size, bool near)
{
// https://github.com/torvalds/linux/commit/223b5e57d0d50b0c07b933350dbcde92018d3080
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0) && !defined(KSU_COMPAT_HAVE_EXECMEM_API)
    u64 base = ksu_inline_get_module_alloc_base();
    u64 module_alloc_end = base + MODULES_VSIZE;
    u64 module_fallback_end;
    unsigned long start;
    unsigned long end;
    gfp_t gfp_mask = GFP_KERNEL;
    void *p;

    if (IS_ENABLED(CONFIG_ARM64_MODULE_PLTS))
        gfp_mask |= __GFP_NOWARN;

    if (IS_ENABLED(CONFIG_KASAN_GENERIC) || IS_ENABLED(CONFIG_KASAN_SW_TAGS))
        module_alloc_end = MODULES_END;

    module_fallback_end = module_alloc_end;

    if (IS_ENABLED(CONFIG_ARM64_MODULE_PLTS) &&
        (IS_ENABLED(CONFIG_KASAN_VMALLOC) || (!IS_ENABLED(CONFIG_KASAN_GENERIC) && !IS_ENABLED(CONFIG_KASAN_SW_TAGS))))
        module_fallback_end = base + SZ_2G;

    if (near) {
        start = (unsigned long)target > KSU_INLINE_BRANCH_RANGE ? (unsigned long)target - KSU_INLINE_BRANCH_RANGE : 0;
        end = (unsigned long)target + KSU_INLINE_BRANCH_RANGE;
        start = max_t(unsigned long, start, base);
        end = min_t(unsigned long, end, module_alloc_end);
        p = ksu_inline_vmalloc_exec_range(size, start, end, gfp_mask);
        if (p)
            return p;

        if (module_fallback_end > module_alloc_end) {
            start =
                (unsigned long)target > KSU_INLINE_BRANCH_RANGE ? (unsigned long)target - KSU_INLINE_BRANCH_RANGE : 0;
            end = (unsigned long)target + KSU_INLINE_BRANCH_RANGE;
            start = max_t(unsigned long, start, module_alloc_end);
            end = min_t(unsigned long, end, module_fallback_end);
            p = ksu_inline_vmalloc_exec_range(size, start, end, GFP_KERNEL);
            if (p)
                return p;
        }

        pr_warn("inline_hook: near alloc failed target=%px size=%zu base=%llx module_end=%llx fallback_end=%llx\n",
                target, size, (unsigned long long)base, (unsigned long long)module_alloc_end,
                (unsigned long long)module_fallback_end);
        return NULL;
    }

    p = ksu_inline_vmalloc_exec_range(size, base, module_alloc_end, gfp_mask);

    if (!p && module_fallback_end > module_alloc_end)
        p = ksu_inline_vmalloc_exec_range(size, base, module_fallback_end, GFP_KERNEL);

    return p;
#else
    (void)target;
    (void)near;
    return execmem_alloc_rw(EXECMEM_DEFAULT, size);
#endif
}

static int ksu_inline_make_entry_stub(struct ksu_inline_hook *hook, void *buf)
{
    u32 *insn = buf;
    unsigned long hook_literal;
    unsigned long dispatcher_literal;
    unsigned long stack_mask_literal;
    u32 *fast_branch = NULL;
    u64 stack_mask = ~((u64)THREAD_SIZE - 1);
    u64 hook_addr = (u64)hook;
    u64 dispatcher = (u64)ksu_inline_hook_arm64_entry_dispatch;
    int ret;

    memset(buf, 0, KSU_INLINE_ENTRY_SIZE);

    hook_literal = (unsigned long)buf + 64;
    dispatcher_literal = hook_literal + sizeof(u64);
    stack_mask_literal = dispatcher_literal + sizeof(u64);

#ifdef CONFIG_ARM64_BTI_KERNEL
    *insn++ = KSU_AARCH64_BTI_JC;
#endif

#ifdef CONFIG_THREAD_INFO_IN_TASK
    // mrs x16 sp_el0
    //
    *insn++ = KSU_AARCH64_MRS_X16_SP_EL0;
    *insn++ = KSU_AARCH64_LDR_X16_X16;
#elif defined(KSU_ARM64_THREAD_INFO_BY_SP)
    *insn++ = KSU_AARCH64_MOV_X16_SP;
    ret = ksu_inline_encode_ldr_literal(17, (unsigned long)insn, stack_mask_literal, insn);
    if (ret)
        return ret;
    insn++;
    *insn++ = KSU_AARCH64_AND_X16_X16_X17;
    *insn++ = KSU_AARCH64_LDR_X16_X16;
#else
    *insn++ = KSU_AARCH64_MRS_X16_SP_EL0;
    *insn++ = KSU_AARCH64_LDR_X16_X16;
#endif
    fast_branch = insn++;

    ret = ksu_inline_encode_ldr_literal(16, (unsigned long)insn, hook_literal, insn);
    if (ret)
        return ret;
    insn++;

    if (ksu_inline_branch_in_range((unsigned long)insn, (unsigned long)ksu_inline_hook_arm64_entry_dispatch)) {
        ret = ksu_inline_encode_branch(KSU_AARCH64_B, (unsigned long)insn,
                                       (unsigned long)ksu_inline_hook_arm64_entry_dispatch, insn);
        if (ret)
            return ret;
        insn++;
    } else {
        ret = ksu_inline_encode_ldr_literal(17, (unsigned long)insn, dispatcher_literal, insn);
        if (ret)
            return ret;
        insn++;
        *insn++ = KSU_AARCH64_BR_X17;
    }

    if (fast_branch) {
        unsigned long fast_label = (unsigned long)insn;

        ret = ksu_inline_encode_branch(KSU_AARCH64_B, fast_label, (unsigned long)hook->clone, insn);
        if (ret)
            return ret;
        insn++;

        ret = ksu_inline_encode_tbz(KSU_AARCH64_TBNZ_X16_TIF_PROC_NON_PRIVILEGE, (unsigned long)fast_branch, fast_label,
                                    fast_branch);
        if (ret)
            return ret;
    }

    while ((unsigned long)insn < hook_literal)
        *insn++ = KSU_AARCH64_NOP;

    memcpy((void *)hook_literal, &hook_addr, sizeof(hook_addr));
    memcpy((void *)dispatcher_literal, &dispatcher, sizeof(dispatcher));
    memcpy((void *)stack_mask_literal, &stack_mask, sizeof(stack_mask));

    return 0;
}

static int ksu_inline_relocate_adr(const struct ksu_inline_reloc_ctx *ctx, u32 insn, unsigned long old_pc,
                                   unsigned long new_pc, u32 *out)
{
    u64 imm = ((insn >> 29) & 0x3) | (((insn >> 5) & 0x7ffff) << 2);
    s64 old_imm = ksu_inline_sign_extend(imm, 21);
    bool adrp = (insn & 0x80000000) != 0;
    s64 new_imm;
    u64 encoded;
    unsigned long dst;

    if (adrp) {
        dst = (old_pc & PAGE_MASK) + (old_imm << PAGE_SHIFT);
        dst = ksu_inline_map_clone_page(ctx, dst);
        new_imm = ((s64)(dst & PAGE_MASK) - (s64)(new_pc & PAGE_MASK)) >> PAGE_SHIFT;
    } else {
        dst = old_pc + old_imm;
        dst = ksu_inline_map_clone_addr(ctx, dst);
        new_imm = (s64)dst - (s64)new_pc;
    }

    if (!ksu_inline_simm_fits(new_imm, 21))
        return -ERANGE;

    encoded = (u64)new_imm & 0x1fffff;
    *out = (insn & 0x9f00001f) | ((encoded & 0x3) << 29) | (((encoded >> 2) & 0x7ffff) << 5);
    return 0;
}

static int ksu_inline_relocate_adr_far(struct ksu_inline_reloc_ctx *ctx, u32 insn, unsigned long old_pc,
                                       unsigned long new_pc, u32 *out)
{
    u64 imm = ((insn >> 29) & 0x3) | (((insn >> 5) & 0x7ffff) << 2);
    s64 old_imm = ksu_inline_sign_extend(imm, 21);
    bool adrp = (insn & 0x80000000) != 0;
    unsigned long literal = ALIGN(ctx->veneer_cursor, 8);
    unsigned long dst;
    u64 value;
    int ret;

    if (!adrp)
        return -ERANGE;
    if (literal + sizeof(value) > ctx->veneer_end)
        return -ENOSPC;

    dst = (old_pc & PAGE_MASK) + (old_imm << PAGE_SHIFT);
    dst = ksu_inline_map_clone_page(ctx, dst);
    value = dst & PAGE_MASK;

    ret = ksu_inline_encode_ldr_literal(insn & 0x1f, new_pc, literal, out);
    if (ret)
        return ret;

    memcpy((void *)literal, &value, sizeof(value));
    ctx->veneer_cursor = literal + sizeof(value);
    return 0;
}

static int ksu_inline_relocate_ldr_literal(const struct ksu_inline_reloc_ctx *ctx, u32 insn, unsigned long old_pc,
                                           unsigned long new_pc, u32 *out)
{
    s64 old_imm = ksu_inline_sign_extend((insn >> 5) & 0x7ffff, 19) << 2;
    unsigned long dst = ksu_inline_map_clone_addr(ctx, old_pc + old_imm);
    s64 new_imm = (s64)dst - (s64)new_pc;
    s64 scaled;

    if (new_imm & 0x3)
        return -ERANGE;

    scaled = new_imm >> 2;
    if (!ksu_inline_simm_fits(scaled, 19))
        return -ERANGE;

    *out = (insn & 0xff00001f) | (((u32)scaled & 0x7ffff) << 5);
    return 0;
}

static int ksu_inline_relocate_b_bl(const struct ksu_inline_reloc_ctx *ctx, u32 insn, unsigned long old_pc,
                                    unsigned long new_pc, u32 *out)
{
    s64 old_imm = ksu_inline_sign_extend(insn & 0x03ffffff, 26) << 2;
    unsigned long dst = ksu_inline_map_clone_addr(ctx, old_pc + old_imm);
    bool link = (insn & 0x80000000) != 0;
    int ret;

    ret = ksu_inline_encode_branch(insn, new_pc, dst, out);
    if (!ret)
        return 0;
    if (ret != -ERANGE)
        return ret;

    return ksu_inline_emit_branch_veneer((struct ksu_inline_reloc_ctx *)ctx, new_pc, dst, link, out);
}

static int ksu_inline_relocate_imm19(const struct ksu_inline_reloc_ctx *ctx, u32 insn, unsigned long old_pc,
                                     unsigned long new_pc, u32 *out)
{
    s64 old_imm = ksu_inline_sign_extend((insn >> 5) & 0x7ffff, 19) << 2;
    unsigned long dst = ksu_inline_map_clone_addr(ctx, old_pc + old_imm);
    u32 branch;
    int ret;

    ret = ksu_inline_encode_imm19(insn, new_pc, dst, out);
    if (!ret)
        return 0;
    if (ret != -ERANGE)
        return ret;

    ret = ksu_inline_emit_branch_veneer((struct ksu_inline_reloc_ctx *)ctx, new_pc, dst, false, &branch);
    if (ret)
        return ret;

    return ksu_inline_encode_imm19(insn, new_pc, new_pc + (ksu_inline_sign_extend(branch & 0x03ffffff, 26) << 2), out);
}

static int ksu_inline_relocate_tbz(const struct ksu_inline_reloc_ctx *ctx, u32 insn, unsigned long old_pc,
                                   unsigned long new_pc, u32 *out)
{
    s64 old_imm = ksu_inline_sign_extend((insn >> 5) & 0x3fff, 14) << 2;
    unsigned long dst = ksu_inline_map_clone_addr(ctx, old_pc + old_imm);
    u32 branch;
    int ret;

    ret = ksu_inline_encode_tbz(insn, new_pc, dst, out);
    if (!ret)
        return 0;
    if (ret != -ERANGE)
        return ret;

    ret = ksu_inline_emit_branch_veneer((struct ksu_inline_reloc_ctx *)ctx, new_pc, dst, false, &branch);
    if (ret)
        return ret;

    return ksu_inline_encode_tbz(insn, new_pc, new_pc + (ksu_inline_sign_extend(branch & 0x03ffffff, 26) << 2), out);
}

static int ksu_inline_relocate_insn(const struct ksu_inline_reloc_ctx *ctx, u32 insn, unsigned long old_pc,
                                    unsigned long new_pc, u32 *out)
{
    if (ksu_inline_is_bti(insn) || ksu_inline_is_reg_branch(insn)) {
        *out = insn;
        return 0;
    }

    if ((insn & 0x7c000000) == 0x14000000)
        return ksu_inline_relocate_b_bl(ctx, insn, old_pc, new_pc, out);

    if ((insn & 0xff000010) == 0x54000000)
        return ksu_inline_relocate_imm19(ctx, insn, old_pc, new_pc, out);

    if ((insn & 0x7e000000) == 0x34000000)
        return ksu_inline_relocate_imm19(ctx, insn, old_pc, new_pc, out);

    if ((insn & 0x7e000000) == 0x36000000)
        return ksu_inline_relocate_tbz(ctx, insn, old_pc, new_pc, out);

    if ((insn & 0x9f000000) == 0x10000000 || (insn & 0x9f000000) == 0x90000000) {
        int ret = ksu_inline_relocate_adr(ctx, insn, old_pc, new_pc, out);

        if (ret != -ERANGE)
            return ret;

        return ksu_inline_relocate_adr_far((struct ksu_inline_reloc_ctx *)ctx, insn, old_pc, new_pc, out);
    }

    if ((insn & 0x3b000000) == 0x18000000)
        return ksu_inline_relocate_ldr_literal(ctx, insn, old_pc, new_pc, out);

    *out = insn;
    return 0;
}

static int ksu_inline_build_reinsn(struct ksu_inline_hook *hook, unsigned long clone, unsigned long *veneer_cursor)
{
    struct ksu_inline_reloc_ctx ctx = {
        .old_base = (unsigned long)hook->target,
        .new_base = clone,
        .size = hook->patch_size,
        .veneer_cursor = clone + hook->patch_size * 2,
        .veneer_end = (unsigned long)hook->code + hook->code_size - KSU_INLINE_ENTRY_SIZE - 15,
    };
    unsigned long old_pc = (unsigned long)hook->target;
    unsigned long new_pc = clone;
    unsigned long i;
    int ret;

    for (i = 0; i < hook->patch_size; i += sizeof(u32)) {
        u32 insn;
        u32 relocated;

        memcpy(&insn, (void *)(old_pc + i), sizeof(insn));
        ret = ksu_inline_relocate_insn(&ctx, insn, old_pc + i, new_pc + i, &relocated);
        if (ret) {
            pr_err("inline_hook: reinsn failed target=%px off=0x%lx insn=%08x ret=%d\n", hook->target, i, insn, ret);
            return ret;
        }

        memcpy((void *)(new_pc + i), &relocated, sizeof(relocated));
    }

    ret = ksu_inline_hook_arch_make_branch((void *)((unsigned long)hook->target + hook->patch_size),
                                           (u8 *)(clone + hook->patch_size), hook->patch_size);
    if (ret)
        return ret;

    *veneer_cursor = ctx.veneer_cursor;
    return 0;
}

static void ksu_inline_code_free(void *code)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0) && !defined(KSU_COMPAT_HAVE_EXECMEM_API)
    vfree(code);
#else
    execmem_free(code);
#endif
}

static int ksu_inline_check_target(struct ksu_inline_hook *hook, unsigned long *size)
{
    *size = 0;

    if ((unsigned long)hook->target & 0x3) {
        ksu_inline_dump_target("unaligned arm64 target", hook, *size);
        return -EINVAL;
    }

    if (!kallsyms_lookup_size_offset((unsigned long)hook->target, size, NULL)) {
        ksu_inline_dump_target("kallsyms size lookup failed", hook, *size);
        return -EOPNOTSUPP;
    }

    if (*size < hook->patch_size || (*size & 0x3)) {
        ksu_inline_dump_target("unsupported target size", hook, *size);
        return -EOPNOTSUPP;
    }

    pr_info("inline_hook: target accepted target=%px (%pS) size=%lu patch_size=%zu\n", hook->target, hook->target,
            *size, hook->patch_size);

    return 0;
}

int ksu_inline_hook_arch_prepare(struct ksu_inline_hook *hook, u8 *patch, size_t patch_size)
{
    void *entry_slot;
    void *clone;
    void *code;
    size_t code_size;
    unsigned long target_size;
    unsigned long veneer_budget;
    unsigned long veneer_cursor;
    int ret;

    if (patch_size != KSU_INLINE_PATCH_SIZE)
        return -EINVAL;

    ret = ksu_inline_check_target(hook, &target_size);
    if (ret)
        return ret;

    veneer_budget = (hook->patch_size / sizeof(u32)) * KSU_INLINE_VENEER_SIZE;
    code_size = PAGE_ALIGN(((unsigned long)hook->target & ~PAGE_MASK) + hook->patch_size * 2 + veneer_budget +
                           KSU_INLINE_ENTRY_SIZE + 15);
    code = ksu_inline_hook_code_alloc(hook->target, code_size, true);
    if (!code) {
        pr_warn("inline_hook: near reinsn alloc failed target=%px (%pS) size=%zu, fallback to far reinsn\n",
                hook->target, hook->target, code_size);
        code = ksu_inline_hook_code_alloc(hook->target, code_size, false);
        if (!code)
            return -ENOMEM;
    }

    hook->keep_storage = true;
    hook->code = code;
    hook->code_size = code_size;

    clone = (u8 *)code + ((unsigned long)hook->target & ~PAGE_MASK);
    hook->clone = clone;

    ret = ksu_inline_build_reinsn(hook, (unsigned long)clone, &veneer_cursor);
    if (ret)
        goto err_free;

    entry_slot = PTR_ALIGN((void *)veneer_cursor, 16);
    ret = ksu_inline_make_entry_stub(hook, entry_slot);
    if (ret)
        goto err_free;
    hook->trampoline = entry_slot;
    flush_icache_range((unsigned long)code, (unsigned long)code + code_size);

    ret = ksu_inline_hook_arch_make_direct_branch(hook->target, entry_slot, patch, patch_size);
    if (ret) {
        pr_info("inline_hook: dispatcher out of +/-128MiB target=%px trampoline=%px, using long jump\n", hook->target,
                entry_slot);
        ret = ksu_inline_hook_arch_make_branch(entry_slot, patch, patch_size);
        if (ret)
            goto err_free;
    }

    pr_info("inline_hook: prepared target=%px mode=reinsn trampoline=%px clone=%px\n", hook->target, entry_slot,
            hook->clone);

    return 0;

err_free:
    ksu_inline_code_free(code);
    hook->trampoline = NULL;
    hook->clone = NULL;
    hook->code = NULL;
    hook->code_size = 0;
    return ret;
}

void ksu_inline_hook_arch_release(struct ksu_inline_hook *hook)
{
    if (!hook->active && hook->code) {
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0) && !defined(KSU_COMPAT_HAVE_EXECMEM_API)
        vfree(hook->code);
#else
        execmem_free(hook->code);
#endif
        hook->code = NULL;
        hook->code_size = 0;
        hook->trampoline = NULL;
        hook->clone = NULL;
    }

    hook->slot = KSU_INLINE_INVALID_SLOT;
}

#endif
