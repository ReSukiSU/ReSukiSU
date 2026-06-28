/* SPDX-License-Identifier: GPL-2.0-only */

#ifdef __aarch64__

#include "hook/inline_hook_internal.h"

#include <linux/errno.h>
#include <linux/kallsyms.h>
#include <linux/kernel.h>
#include <linux/moduleloader.h>
#include <linux/numa.h>
#include <linux/set_memory.h>
#include <linux/string.h>
#include <linux/vmalloc.h>
#include <linux/version.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 10, 0)
#include <linux/execmem.h>
#endif

#include <asm/memory.h>
#include <asm/module.h>
#include <asm/sections.h>

#include <linux/gfp.h>
#include <linux/mm.h>
#include "hook/patch_memory.h"

#ifdef CONFIG_ARM64_BTI_KERNEL
#define KSU_INLINE_PATCH_SIZE 20
#else
#define KSU_INLINE_PATCH_SIZE 16
#endif
#define KSU_INLINE_ENTRY_SIZE 48
#define KSU_INLINE_VENEER_SIZE 32
#define KSU_AARCH64_LDR_X16_8 0x58000050
#define KSU_AARCH64_LDR_X16_12 0x58000070
#define KSU_AARCH64_LDR_X16_16 0x58000090
#define KSU_AARCH64_LDR_X17_20 0x580000b1
#define KSU_AARCH64_BR_X16 0xd61f0200
#define KSU_AARCH64_BR_X17 0xd61f0220
#define KSU_AARCH64_BLR_X16 0xd63f0200
#define KSU_AARCH64_RET 0xd65f03c0
#define KSU_AARCH64_STP_X16_X30_PRE 0xa9bf7bf0
#define KSU_AARCH64_LDP_X16_X30_POST 0xa8c17bf0
#define KSU_AARCH64_NOP 0xd503201f
#define KSU_AARCH64_BTI_JC 0xd50324df

extern void ksu_inline_hook_arm64_entry_with_after(void);
extern void ksu_inline_hook_arm64_entry_with_before(void);
extern void ksu_inline_hook_arm64_entry_with_before_and_after(void);

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

static inline void *ksu_inline_hook_clone_code_alloc(size_t size)
{
// https://github.com/torvalds/linux/commit/223b5e57d0d50b0c07b933350dbcde92018d3080
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0)
    u64 module_alloc_end = module_alloc_base + MODULES_VSIZE;

    if (IS_ENABLED(CONFIG_KASAN))
        module_alloc_end = MODULES_END;

    return __vmalloc_node_range(size, MODULE_ALIGN, module_alloc_base, module_alloc_end, GFP_KERNEL, PAGE_KERNEL_EXEC,
                                0, NUMA_NO_NODE, __builtin_return_address(0));
#else
    return execmem_alloc(EXECMEM_DEFAULT, size);
#endif
}

static void ksu_inline_make_entry_stub(struct ksu_inline_hook *hook, void *buf)
{
    u32 *insn = buf;
    u64 literal[3];
    void *entry;

    if (hook->before && hook->after)
        entry = ksu_inline_hook_arm64_entry_with_before_and_after;
    else if (hook->before)
        entry = ksu_inline_hook_arm64_entry_with_before;
    else
        entry = ksu_inline_hook_arm64_entry_with_after;

#ifdef CONFIG_ARM64_BTI_KERNEL
    insn[0] = KSU_AARCH64_BTI_JC;
    if (hook->before) {
        insn[1] = KSU_AARCH64_LDR_X16_12;
        insn[2] = 0x58000091; /* ldr x17, #16 -> slot + 24 */
        insn[3] = KSU_AARCH64_BR_X17;
        literal[0] = (u64)hook;
        literal[1] = (u64)entry;
        memcpy((u8 *)buf + 16, literal, sizeof(literal[0]) * 2);
    } else {
        insn[1] = 0x580000b0; /* ldr x16, #20 -> slot + 24 */
        insn[2] = 0x580000d1; /* ldr x17, #24 -> slot + 32 */
        insn[3] = 0x580000e9; /* ldr x9, #28 -> slot + 40 */
        insn[4] = 0xd61f0120; /* br x9 */
        insn[5] = KSU_AARCH64_NOP;
        literal[0] = (u64)hook;
        literal[1] = (u64)hook->clone;
        literal[2] = (u64)entry;
        memcpy((u8 *)buf + 24, literal, sizeof(literal));
    }
#else
    insn[0] = KSU_AARCH64_LDR_X16_16;
    insn[1] = KSU_AARCH64_LDR_X17_20;
    if (hook->before) {
        insn[2] = KSU_AARCH64_BR_X17;
        literal[0] = (u64)hook;
        literal[1] = (u64)entry;
        memcpy((u8 *)buf + 16, literal, sizeof(literal[0]) * 2);
    } else {
        insn[2] = 0x580000c9; /* ldr x9, #24 -> slot + 32 */
        insn[3] = 0xd61f0120; /* br x9 */
        literal[0] = (u64)hook;
        literal[1] = (u64)hook->clone;
        literal[2] = (u64)entry;
        memcpy((u8 *)buf + 16, literal, sizeof(literal));
    }
#endif
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

    if ((insn & 0x9f000000) == 0x10000000 || (insn & 0x9f000000) == 0x90000000)
        return ksu_inline_relocate_adr(ctx, insn, old_pc, new_pc, out);

    if ((insn & 0x3b000000) == 0x18000000)
        return ksu_inline_relocate_ldr_literal(ctx, insn, old_pc, new_pc, out);

    *out = insn;
    return 0;
}

static int ksu_inline_build_clone(struct ksu_inline_hook *hook, unsigned long clone, unsigned long size,
                                  unsigned long *veneer_cursor)
{
    struct ksu_inline_reloc_ctx ctx = {
        .old_base = (unsigned long)hook->target,
        .new_base = clone,
        .size = size,
        .veneer_cursor = clone + size,
        .veneer_end = (unsigned long)hook->code + hook->code_size - KSU_INLINE_ENTRY_SIZE - 15,
    };
    unsigned long old_pc = (unsigned long)hook->target;
    unsigned long new_pc = clone;
    unsigned long i;
    int ret;

    for (i = 0; i < size; i += sizeof(u32)) {
        u32 insn;
        u32 relocated;

        memcpy(&insn, (void *)(old_pc + i), sizeof(insn));
        ret = ksu_inline_relocate_insn(&ctx, insn, old_pc + i, new_pc + i, &relocated);
        if (ret) {
            pr_err("inline_hook: relocate failed target=%px off=0x%lx insn=%08x ret=%d\n", hook->target, i, insn, ret);
            return ret;
        }

        memcpy((void *)(new_pc + i), &relocated, sizeof(relocated));
    }

    *veneer_cursor = ctx.veneer_cursor;
    return 0;
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
    u8 entry[KSU_INLINE_ENTRY_SIZE] __aligned(16);
    void *entry_slot;
    void *clone;
    void *code;
    size_t code_size;
    unsigned long clone_size;
    unsigned long veneer_budget;
    unsigned long veneer_cursor;
    int pages;
    int ret;

    if (patch_size != KSU_INLINE_PATCH_SIZE)
        return -EINVAL;

    ret = ksu_inline_check_target(hook, &clone_size);
    if (ret)
        return ret;

    // clear entry
    for (ret = 0; ret < ARRAY_SIZE(entry) / sizeof(u32); ret++)
        ((u32 *)entry)[ret] = KSU_AARCH64_NOP;

    // calculate how much memory we need and alloc it
    veneer_budget = (clone_size / sizeof(u32)) * KSU_INLINE_VENEER_SIZE;
    code_size = PAGE_ALIGN(((unsigned long)hook->target & ~PAGE_MASK) + clone_size + veneer_budget +
                           KSU_INLINE_ENTRY_SIZE + 15);
    code = ksu_inline_hook_clone_code_alloc(code_size);
    if (!code)
        return -ENOMEM;

    hook->keep_storage = true;
    hook->code = code;
    hook->code_size = code_size;

    // keep same page offset
    clone = (u8 *)code + ((unsigned long)hook->target & ~PAGE_MASK);
    hook->clone = clone;

    // insn copy and relocate
    ret = ksu_inline_build_clone(hook, (unsigned long)clone, clone_size, &veneer_cursor);
    if (ret)
        goto err_free;

    entry_slot = PTR_ALIGN((void *)veneer_cursor, 16);
    // init first trampoline
    ksu_inline_make_entry_stub(hook, entry);
    memcpy(entry_slot, entry, sizeof(entry));

    ret = ksu_inline_hook_arch_make_branch(entry_slot, patch, patch_size);
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
