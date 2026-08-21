/* SPDX-License-Identifier: GPL-2.0-only */

#ifdef __arm__

#include "hook/inline_hook_internal.h"

#include <linux/errno.h>
#include <linux/gfp.h>
#include <linux/kallsyms.h>
#include <linux/mm.h>
#include <linux/numa.h>
#include <linux/sizes.h>
#include <linux/string.h>
#include <linux/vmalloc.h>
#include <linux/version.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 0, 0)
#include <linux/kasan.h>
#endif
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 10, 0)
#include <linux/execmem.h>
#endif

#include <asm/cacheflush.h>
#ifdef CONFIG_THREAD_INFO_IN_TASK
#include <asm/current.h>
#endif
#include <asm/memory.h>
#include <asm/module.h>
#include <asm/opcodes.h>
#include <asm/pgtable.h>
#include <asm/sections.h>
#include <asm/thread_info.h>

#include "compat/kernel_compat.h"
#include "feature/sucompat.h"

#ifdef CONFIG_THUMB2_KERNEL
#define KSU_ARM32_PREFERRED_PATCH_SIZE 10
#define KSU_ARM32_ABS_BRANCH_SIZE 10
#define KSU_ARM32_BRANCH_RANGE SZ_16M
#define KSU_ARM32_BRANCH_PC_BIAS 4
#else
#define KSU_ARM32_PREFERRED_PATCH_SIZE 8
#define KSU_ARM32_ABS_BRANCH_SIZE 8
#define KSU_ARM32_BRANCH_RANGE SZ_32M
#define KSU_ARM32_BRANCH_PC_BIAS 8
#endif
#define KSU_ARM32_DIRECT_BRANCH_SIZE 4
#define KSU_ARM32_LITERAL_BRANCH_SIZE 8
#define KSU_ARM32_ENTRY_SIZE 80
#define KSU_ARM32_ENTRY_LITERAL_OFFSET 64
#define KSU_ARM32_ENTRY_HOOK_LITERAL 64
#define KSU_ARM32_ENTRY_CLONE_LITERAL 68
#define KSU_ARM32_ENTRY_DISPATCH_LITERAL 72
#define KSU_ARM32_ENTRY_CURRENT_LITERAL 76
#define KSU_ARM32_MAX_VENEER_SIZE 16
#define KSU_ARM32_ARM_NOP 0xe1a00000
#define KSU_ARM32_THUMB_NOP 0xbf00

#ifdef CONFIG_XIP_KERNEL
#undef MODULES_VADDR
#define MODULES_VADDR (((unsigned long)_exiprom + ~PMD_MASK) & PMD_MASK)
#endif

extern void ksu_inline_hook_arm32_entry_with_after(void);
extern void ksu_inline_hook_arm32_entry_with_before(void);
extern void ksu_inline_hook_arm32_entry_with_before_and_after(void);

static inline s32 ksu_arm32_sign_extend(u32 value, unsigned int bits)
{
    return (s32)(value << (32 - bits)) >> (32 - bits);
}

static inline bool ksu_arm32_simm_fits(s64 value, unsigned int bits)
{
    s64 min = -(1LL << (bits - 1));
    s64 max = (1LL << (bits - 1)) - 1;

    return value >= min && value <= max;
}

static u32 ksu_arm32_read_arm(const void *addr)
{
    u32 value;

    memcpy(&value, addr, sizeof(value));
    return __mem_to_opcode_arm(value);
}

static void ksu_arm32_write_arm(void *addr, u32 opcode)
{
    u32 value = __opcode_to_mem_arm(opcode);

    memcpy(addr, &value, sizeof(value));
}

static u16 ksu_arm32_read_thumb16(const void *addr)
{
    u16 value;

    memcpy(&value, addr, sizeof(value));
    return __mem_to_opcode_thumb16(value);
}

static void ksu_arm32_write_thumb16(void *addr, u16 opcode)
{
    u16 value = __opcode_to_mem_thumb16(opcode);

    memcpy(addr, &value, sizeof(value));
}

static u32 ksu_arm32_read_thumb32(const void *addr)
{
    u16 first = ksu_arm32_read_thumb16(addr);
    u16 second = ksu_arm32_read_thumb16((const u8 *)addr + sizeof(first));

    return __opcode_thumb32_compose(first, second);
}

static void ksu_arm32_write_thumb32(void *addr, u32 opcode)
{
    ksu_arm32_write_thumb16(addr, __opcode_thumb32_first(opcode));
    ksu_arm32_write_thumb16((u8 *)addr + sizeof(u16), __opcode_thumb32_second(opcode));
}

static u32 ksu_arm32_encode_thumb_mov_imm16(bool top, u32 rd, u16 imm)
{
    u16 first = (top ? 0xf2c0 : 0xf240) | (((imm >> 11) & 1) << 10) | ((imm >> 12) & 0xf);
    u16 second = (((imm >> 8) & 0x7) << 12) | ((rd & 0xf) << 8) | (imm & 0xff);

    return __opcode_thumb32_compose(first, second);
}

static u32 ksu_arm32_encode_thumb_shift_imm(u32 type, u32 rd, u32 rm, u32 imm)
{
    u16 first = 0xea4f;
    u16 second = (((imm >> 2) & 0x7) << 12) | ((rd & 0xf) << 8) | ((imm & 0x3) << 6) | ((type & 0x3) << 4) | (rm & 0xf);

    return __opcode_thumb32_compose(first, second);
}

static inline bool ksu_arm32_thumb_is_wide(u16 first)
{
    return (first & 0xf800) == 0xe800 || (first & 0xf000) == 0xf000;
}

static inline void *ksu_arm32_code_ptr(void *addr)
{
#ifdef CONFIG_THUMB2_KERNEL
    return (void *)((unsigned long)addr | 1UL);
#else
    return addr;
#endif
}

static int ksu_arm32_encode_arm_branch(u32 opcode, unsigned long from, unsigned long to, u32 *out)
{
    s64 diff = (s64)(to & ~1UL) - (s64)(from + 8);

    if (diff & 0x3)
        return -ERANGE;
    if (!ksu_arm32_simm_fits(diff, 26))
        return -ERANGE;

    *out = (opcode & 0xff000000) | (((u32)diff >> 2) & 0x00ffffff);
    return 0;
}

static int ksu_arm32_encode_thumb_branch(bool link, unsigned long from, unsigned long to, u32 *out)
{
    s64 diff = (s64)(to & ~1UL) - (s64)(from + 4);
    u32 imm;
    u32 s;
    u32 i1;
    u32 i2;
    u32 j1;
    u32 j2;
    u16 first;
    u16 second;

    if (diff & 0x1)
        return -ERANGE;
    if (!ksu_arm32_simm_fits(diff, 25))
        return -ERANGE;

    imm = (u32)diff & 0x01ffffff;
    s = (imm >> 24) & 1;
    i1 = (imm >> 23) & 1;
    i2 = (imm >> 22) & 1;
    j1 = !(i1 ^ s);
    j2 = !(i2 ^ s);
    first = 0xf000 | (s << 10) | ((imm >> 12) & 0x03ff);
    second = 0x9000 | (link ? 0x4000 : 0) | (j1 << 13) | (j2 << 11) | ((imm >> 1) & 0x07ff);
    *out = __opcode_thumb32_compose(first, second);
    return 0;
}

static int ksu_arm32_make_direct_branch(unsigned long from, unsigned long to, u8 *patch, size_t patch_size)
{
    int ret;

    if (patch_size < sizeof(u32))
        return -EINVAL;

#ifdef CONFIG_THUMB2_KERNEL
    u32 branch;
    size_t offset;

    ret = ksu_arm32_encode_thumb_branch(false, from, to, &branch);
    if (ret)
        return ret;

    ksu_arm32_write_thumb32(patch, branch);
    for (offset = sizeof(branch); offset < patch_size; offset += sizeof(u16))
        ksu_arm32_write_thumb16(patch + offset, KSU_ARM32_THUMB_NOP);
#else
    u32 branch;
    size_t offset;

    ret = ksu_arm32_encode_arm_branch(0xea000000, from, to, &branch);
    if (ret)
        return ret;

    ksu_arm32_write_arm(patch, branch);
    for (offset = sizeof(branch); offset < patch_size; offset += sizeof(u32))
        ksu_arm32_write_arm(patch + offset, KSU_ARM32_ARM_NOP);
#endif

    return 0;
}

void *ksu_inline_hook_arch_normalize_target(void *target)
{
    return (void *)((unsigned long)target & ~1UL);
}

size_t ksu_inline_hook_arch_patch_size(void *target)
{
    unsigned long target_size = 0;
    size_t minimum;
    size_t size;

    if (!target || !kallsyms_lookup_size_offset((unsigned long)target, &target_size, NULL) ||
        target_size < KSU_ARM32_DIRECT_BRANCH_SIZE)
        return 0;

    /* Tiny syscall thunks can only hold the direct branch form. */
    minimum =
        target_size >= KSU_ARM32_PREFERRED_PATCH_SIZE ? KSU_ARM32_PREFERRED_PATCH_SIZE : KSU_ARM32_DIRECT_BRANCH_SIZE;

#ifdef CONFIG_THUMB2_KERNEL
    size = 0;

    while (size < minimum) {
        u16 first;
        size_t insn_size;

        if (size + sizeof(first) > target_size)
            return 0;

        first = ksu_arm32_read_thumb16((u8 *)target + size);
        insn_size = ksu_arm32_thumb_is_wide(first) ? sizeof(u32) : sizeof(u16);
        if (size + insn_size > target_size || size + insn_size > KSU_INLINE_MAX_PATCH_SIZE)
            return 0;
        size += insn_size;
    }

#else
    size = minimum;
    if ((unsigned long)target & (sizeof(u32) - 1))
        return 0;
#endif

    if (((unsigned long)target & ~PAGE_MASK) + size > PAGE_SIZE)
        return 0;

    return size;
}

int ksu_inline_hook_arch_make_branch(void *to, u8 *patch, size_t patch_size)
{
    u32 addr = (u32)to;

    if (patch_size < KSU_ARM32_ABS_BRANCH_SIZE)
        return -EINVAL;

#ifdef CONFIG_THUMB2_KERNEL
    size_t offset;

    memset(patch, 0, patch_size);
    ksu_arm32_write_thumb32(patch, ksu_arm32_encode_thumb_mov_imm16(false, 12, (u16)addr));
    ksu_arm32_write_thumb32(patch + sizeof(u32), ksu_arm32_encode_thumb_mov_imm16(true, 12, (u16)(addr >> 16)));
    ksu_arm32_write_thumb16(patch + 2 * sizeof(u32), 0x4760); /* bx ip */
    for (offset = KSU_ARM32_ABS_BRANCH_SIZE; offset < patch_size; offset += sizeof(u16))
        ksu_arm32_write_thumb16(patch + offset, KSU_ARM32_THUMB_NOP);
#else
    size_t offset;

    memset(patch, 0, patch_size);
    ksu_arm32_write_arm(patch, 0xe51ff004); /* ldr pc, [pc, #-4] */
    memcpy(patch + sizeof(u32), &addr, sizeof(addr));
    for (offset = KSU_ARM32_ABS_BRANCH_SIZE; offset < patch_size; offset += sizeof(u32))
        ksu_arm32_write_arm(patch + offset, KSU_ARM32_ARM_NOP);
#endif

    return 0;
}

unsigned long ksu_inline_hook_arch_get_ret(const struct pt_regs *regs)
{
    return regs->ARM_r0;
}

void ksu_inline_hook_arch_setup_regs(struct pt_regs *regs, unsigned long *arg_regs)
{
    if (!arg_regs)
        return;

    regs->ARM_r0 = arg_regs[0];
    regs->ARM_r1 = arg_regs[1];
    regs->ARM_r2 = arg_regs[2];
    regs->ARM_r3 = arg_regs[3];
    regs->ARM_ORIG_r0 = arg_regs[0];
    regs->ARM_sp = arg_regs[4];
}

void ksu_inline_hook_arch_update_args(const struct pt_regs *regs, unsigned long *arg_regs)
{
    if (!arg_regs)
        return;

    arg_regs[0] = regs->ARM_r0;
    arg_regs[1] = regs->ARM_r1;
    arg_regs[2] = regs->ARM_r2;
    arg_regs[3] = regs->ARM_r3;
    arg_regs[4] = regs->ARM_sp;
}

void ksu_inline_hook_arch_set_ret(struct pt_regs *regs, unsigned long ret)
{
    regs->ARM_r0 = ret;
}

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

#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0) && !defined(KSU_COMPAT_HAVE_EXECMEM_API)
static void *ksu_arm32_vmalloc_exec_range(size_t size, unsigned long start, unsigned long end, gfp_t gfp_mask)
{
    if (end <= start || end - start < PAGE_ALIGN(size))
        return NULL;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 0, 0) || defined(KSU_COMPAT_HAVE_VMFLAGS_IN_VMALLOC_NODE_RANGE)
    return __vmalloc_node_range(size, 1, start, end, gfp_mask, PAGE_KERNEL_EXEC, 0, NUMA_NO_NODE,
                                __builtin_return_address(0));
#else
    return __vmalloc_node_range(size, 1, start, end, gfp_mask, PAGE_KERNEL_EXEC, NUMA_NO_NODE,
                                __builtin_return_address(0));
#endif
}
#endif

static void *ksu_inline_hook_code_alloc(void *target, size_t size, bool near)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0) && !defined(KSU_COMPAT_HAVE_EXECMEM_API)
    gfp_t gfp_mask = GFP_KERNEL;
    unsigned long start = MODULES_VADDR;
    unsigned long end = MODULES_END;
    void *p;

    if (IS_ENABLED(CONFIG_ARM_MODULE_PLTS))
        gfp_mask |= __GFP_NOWARN;

    /* A short target patch requires its per-hook entry to stay in branch range. */
    if (near) {
        unsigned long target_addr = (unsigned long)target;
        unsigned long branch_pc = target_addr + KSU_ARM32_BRANCH_PC_BIAS;

        start =
            max_t(unsigned long, start, branch_pc > KSU_ARM32_BRANCH_RANGE ? branch_pc - KSU_ARM32_BRANCH_RANGE : 0);
        end = min_t(unsigned long, end, branch_pc + KSU_ARM32_BRANCH_RANGE);
    }
    p = ksu_arm32_vmalloc_exec_range(size, start, end, gfp_mask);
    if (!IS_ENABLED(CONFIG_ARM_MODULE_PLTS) || p)
        goto out;

    start = VMALLOC_START;
    end = VMALLOC_END;
    if (near) {
        unsigned long target_addr = (unsigned long)target;
        unsigned long branch_pc = target_addr + KSU_ARM32_BRANCH_PC_BIAS;

        start =
            max_t(unsigned long, start, branch_pc > KSU_ARM32_BRANCH_RANGE ? branch_pc - KSU_ARM32_BRANCH_RANGE : 0);
        end = min_t(unsigned long, end, branch_pc + KSU_ARM32_BRANCH_RANGE);
        if (end <= start)
            goto out;
    }

    p = ksu_arm32_vmalloc_exec_range(size, start, end, GFP_KERNEL);

out:
    if (p && ksu_inline_kasan_module_alloc(p, size, gfp_mask) < 0) {
        vfree(p);
        return NULL;
    }

    return ksu_inline_kasan_reset_tag(p);
#else
    return execmem_alloc_rw(EXECMEM_DEFAULT, size);
#endif
}

static void ksu_inline_code_free(void *code)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0) && !defined(KSU_COMPAT_HAVE_EXECMEM_API)
    vfree(code);
#else
    execmem_free(code);
#endif
}

struct ksu_arm32_reloc_ctx {
    unsigned long old_base;
    unsigned long new_base;
    unsigned long size;
    unsigned long veneer_cursor;
    unsigned long veneer_end;
};

static inline unsigned long ksu_arm32_map_clone_addr(const struct ksu_arm32_reloc_ctx *ctx, unsigned long addr)
{
    unsigned long state = addr & 1UL;

    addr &= ~1UL;
    if (addr >= ctx->old_base && addr < ctx->old_base + ctx->size)
        addr = ctx->new_base + (addr - ctx->old_base);

    return addr | state;
}

static int ksu_arm32_emit_branch_veneer(struct ksu_arm32_reloc_ctx *ctx, unsigned long dst, unsigned long *veneer_addr)
{
    unsigned long veneer = ALIGN(ctx->veneer_cursor, sizeof(u32));
    u32 addr = (u32)dst;

    if (veneer + KSU_ARM32_LITERAL_BRANCH_SIZE > ctx->veneer_end)
        return -ENOSPC;

#ifdef CONFIG_THUMB2_KERNEL
    ksu_arm32_write_thumb32((void *)veneer, 0xf8dff000); /* ldr.w pc, [pc] */
#else
    ksu_arm32_write_arm((void *)veneer, 0xe51ff004); /* ldr pc, [pc, #-4] */
#endif
    memcpy((void *)(veneer + sizeof(u32)), &addr, sizeof(addr));
    ctx->veneer_cursor = veneer + KSU_ARM32_LITERAL_BRANCH_SIZE;
    *veneer_addr = veneer;
    return 0;
}

#ifndef CONFIG_THUMB2_KERNEL
static u32 ksu_arm32_expand_arm_imm(u32 insn)
{
    u32 value = insn & 0xff;
    unsigned int shift = ((insn >> 8) & 0xf) * 2;

    if (!shift)
        return value;
    return (value >> shift) | (value << (32 - shift));
}

static int ksu_arm32_emit_arm_value_veneer(struct ksu_arm32_reloc_ctx *ctx, u32 rd, u32 value, unsigned long return_pc,
                                           unsigned long *veneer_addr)
{
    unsigned long veneer = ALIGN(ctx->veneer_cursor, sizeof(u32));
    u32 branch;

    if (veneer + 16 > ctx->veneer_end)
        return -ENOSPC;
    if (ksu_arm32_encode_arm_branch(0xea000000, veneer + 4, return_pc, &branch))
        return -ERANGE;

    ksu_arm32_write_arm((void *)veneer, 0xe59f0004 | (rd << 12));
    ksu_arm32_write_arm((void *)(veneer + 4), branch);
    ksu_arm32_write_arm((void *)(veneer + 8), KSU_ARM32_ARM_NOP);
    memcpy((void *)(veneer + 12), &value, sizeof(value));
    ctx->veneer_cursor = veneer + 16;
    *veneer_addr = veneer;
    return 0;
}

static int ksu_arm32_relocate_arm_adr(struct ksu_arm32_reloc_ctx *ctx, u32 insn, unsigned long old_pc,
                                      unsigned long new_pc, u32 *out)
{
    u32 opcode = (insn >> 21) & 0xf;
    u32 rd = (insn >> 12) & 0xf;
    u32 value;
    unsigned long veneer;
    int ret;

    if ((insn & 0x0e000000) != 0x02000000 || ((insn >> 16) & 0xf) != 0xf || (insn & BIT(20)) ||
        (opcode != 0x4 && opcode != 0x2))
        return -ENOEXEC;
    if (rd == 0xf)
        return -EOPNOTSUPP;

    value = old_pc + 8;
    if (opcode == 0x4)
        value += ksu_arm32_expand_arm_imm(insn);
    else
        value -= ksu_arm32_expand_arm_imm(insn);
    value = ksu_arm32_map_clone_addr(ctx, value);

    ret = ksu_arm32_emit_arm_value_veneer(ctx, rd, value, new_pc + sizeof(u32), &veneer);
    if (ret)
        return ret;

    return ksu_arm32_encode_arm_branch((insn & 0xf0000000) | 0x0a000000, new_pc, veneer, out);
}

static int ksu_arm32_relocate_arm_literal(struct ksu_arm32_reloc_ctx *ctx, u32 insn, unsigned long old_pc,
                                          unsigned long new_pc, u32 *out)
{
    unsigned long veneer = ALIGN(ctx->veneer_cursor, sizeof(u32));
    unsigned long literal_addr;
    u32 rt = (insn >> 12) & 0xf;
    u32 load;
    u32 branch;
    u32 literal;
    int ret;

    if ((insn & 0x0c000000) != 0x04000000 || (insn & BIT(25)) || !(insn & BIT(24)) || (insn & BIT(21)) ||
        !(insn & BIT(20)) || ((insn >> 16) & 0xf) != 0xf)
        return -ENOEXEC;
    if (rt == 0xf)
        return -EOPNOTSUPP;
    if (veneer + 16 > ctx->veneer_end)
        return -ENOSPC;

    literal_addr = old_pc + 8;
    if (insn & BIT(23))
        literal_addr += insn & 0xfff;
    else
        literal_addr -= insn & 0xfff;
    literal_addr = ksu_arm32_map_clone_addr(ctx, literal_addr);
    literal = literal_addr;

    load = (insn & 0x0ff0f000) | 0xe0000000 | (rt << 16) | BIT(23);
    ret = ksu_arm32_encode_arm_branch(0xea000000, veneer + 8, new_pc + sizeof(u32), &branch);
    if (ret)
        return ret;

    ksu_arm32_write_arm((void *)veneer, 0xe59f0004 | (rt << 12));
    ksu_arm32_write_arm((void *)(veneer + 4), load);
    ksu_arm32_write_arm((void *)(veneer + 8), branch);
    memcpy((void *)(veneer + 12), &literal, sizeof(literal));
    ctx->veneer_cursor = veneer + 16;

    return ksu_arm32_encode_arm_branch((insn & 0xf0000000) | 0x0a000000, new_pc, veneer, out);
}

static bool ksu_arm32_arm_uses_pc(u32 insn)
{
    u32 class = insn & 0x0c000000;

    if ((insn & 0x0ffffff0) == 0x012fff10 || (insn & 0x0ffffff0) == 0x012fff30)
        return (insn & 0xf) == 0xf;

    if (class == 0x04000000)
        return ((insn >> 16) & 0xf) == 0xf || (!(insn & BIT(20)) && ((insn >> 12) & 0xf) == 0xf);

    if ((insn & 0x0e000000) == 0x08000000)
        return ((insn >> 16) & 0xf) == 0xf || (!(insn & BIT(20)) && (insn & BIT(15)));

    if ((insn & 0x0e000000) == 0x0c000000)
        return ((insn >> 16) & 0xf) == 0xf;

    if (class == 0) {
        u32 opcode = (insn >> 21) & 0xf;
        bool uses_rn = opcode != 0xd && opcode != 0xf;

        if (uses_rn && ((insn >> 16) & 0xf) == 0xf)
            return true;
        if (!(insn & BIT(25))) {
            if ((insn & 0xf) == 0xf)
                return true;
            if ((insn & BIT(4)) && ((insn >> 8) & 0xf) == 0xf)
                return true;
        }
    }

    return false;
}

static int ksu_arm32_relocate_arm_insn(struct ksu_arm32_reloc_ctx *ctx, u32 insn, unsigned long old_pc,
                                       unsigned long new_pc, u32 *out)
{
    if ((insn & 0x0e000000) == 0x0a000000 && (insn >> 28) != 0xf) {
        unsigned long dst = old_pc + 8 + ksu_arm32_sign_extend((insn & 0x00ffffff) << 2, 26);
        unsigned long veneer;
        int ret;

        dst = ksu_arm32_map_clone_addr(ctx, dst);
        ret = ksu_arm32_encode_arm_branch(insn, new_pc, dst, out);
        if (!ret)
            return 0;
        if (ret != -ERANGE)
            return ret;

        ret = ksu_arm32_emit_branch_veneer(ctx, dst, &veneer);
        if (ret)
            return ret;
        return ksu_arm32_encode_arm_branch(insn, new_pc, veneer, out);
    }

    if ((insn & 0xfe000000) == 0xfa000000)
        return -EOPNOTSUPP;

    if ((insn & 0x0c1f0000) == 0x041f0000) {
        int ret = ksu_arm32_relocate_arm_literal(ctx, insn, old_pc, new_pc, out);

        if (ret != -ENOEXEC)
            return ret;
    }

    if ((insn & 0x0e0f0000) == 0x020f0000) {
        int ret = ksu_arm32_relocate_arm_adr(ctx, insn, old_pc, new_pc, out);

        if (ret != -ENOEXEC)
            return ret;
    }

    if (ksu_arm32_arm_uses_pc(insn))
        return -EOPNOTSUPP;

    *out = insn;
    return 0;
}
#else /* CONFIG_THUMB2_KERNEL */
static int ksu_arm32_encode_thumb16_branch(u16 insn, unsigned long from, unsigned long to, u16 *out)
{
    s64 diff = (s64)(to & ~1UL) - (s64)(from + 4);

    if (diff & 1)
        return -ERANGE;

    if ((insn & 0xf000) == 0xd000) {
        if (!ksu_arm32_simm_fits(diff, 9))
            return -ERANGE;
        *out = (insn & 0xff00) | (((u16)diff >> 1) & 0xff);
        return 0;
    }

    if ((insn & 0xf800) == 0xe000) {
        if (!ksu_arm32_simm_fits(diff, 12))
            return -ERANGE;
        *out = (insn & 0xf800) | (((u16)diff >> 1) & 0x07ff);
        return 0;
    }

    return -EINVAL;
}

static int ksu_arm32_encode_thumb_cbz(u16 insn, unsigned long from, unsigned long to, u16 *out)
{
    s64 diff = (s64)(to & ~1UL) - (s64)(from + 4);

    if (diff < 0 || diff > 126 || (diff & 1))
        return -ERANGE;

    *out = (insn & ~0x02f8) | (((u16)diff & 0x40) << 3) | (((u16)diff & 0x3e) << 2);
    return 0;
}

static int ksu_arm32_emit_thumb_literal_veneer(struct ksu_arm32_reloc_ctx *ctx, u16 insn, unsigned long old_pc,
                                               unsigned long new_pc, u16 *out)
{
    unsigned long veneer = ALIGN(ctx->veneer_cursor, sizeof(u32));
    unsigned long literal_addr;
    u32 branch;
    u32 literal;
    u32 rt = (insn >> 8) & 0x7;
    u16 jump;
    int ret;

    if (veneer + 16 > ctx->veneer_end)
        return -ENOSPC;

    literal_addr = ALIGN(old_pc + 4, sizeof(u32)) + ((insn & 0xff) << 2);
    literal_addr = ksu_arm32_map_clone_addr(ctx, literal_addr);
    literal = literal_addr;

    ksu_arm32_write_thumb32((void *)veneer, 0xf8df0008 | (rt << 12));
    ksu_arm32_write_thumb16((void *)(veneer + 4), 0x6800 | (rt << 3) | rt);
    ret = ksu_arm32_encode_thumb_branch(false, veneer + 6, new_pc + sizeof(u16), &branch);
    if (ret)
        return ret;
    ksu_arm32_write_thumb32((void *)(veneer + 6), branch);
    ksu_arm32_write_thumb16((void *)(veneer + 10), KSU_ARM32_THUMB_NOP);
    memcpy((void *)(veneer + 12), &literal, sizeof(literal));
    ctx->veneer_cursor = veneer + 16;

    ret = ksu_arm32_encode_thumb16_branch(0xe000, new_pc, veneer, &jump);
    if (ret)
        return ret;
    *out = jump;
    return 0;
}

static int ksu_arm32_emit_thumb_adr_veneer(struct ksu_arm32_reloc_ctx *ctx, u16 insn, unsigned long old_pc,
                                           unsigned long new_pc, u16 *out)
{
    unsigned long veneer = ALIGN(ctx->veneer_cursor, sizeof(u32));
    u32 value;
    u32 branch;
    u32 rd = (insn >> 8) & 0x7;
    u16 jump;
    int ret;

    if (veneer + 12 > ctx->veneer_end)
        return -ENOSPC;

    value = ALIGN(old_pc + 4, sizeof(u32)) + ((insn & 0xff) << 2);
    value = ksu_arm32_map_clone_addr(ctx, value);

    ksu_arm32_write_thumb32((void *)veneer, 0xf8df0004 | (rd << 12));
    ret = ksu_arm32_encode_thumb_branch(false, veneer + 4, new_pc + sizeof(u16), &branch);
    if (ret)
        return ret;
    ksu_arm32_write_thumb32((void *)(veneer + 4), branch);
    memcpy((void *)(veneer + 8), &value, sizeof(value));
    ctx->veneer_cursor = veneer + 12;

    ret = ksu_arm32_encode_thumb16_branch(0xe000, new_pc, veneer, &jump);
    if (ret)
        return ret;
    *out = jump;
    return 0;
}

static int ksu_arm32_relocate_thumb16(struct ksu_arm32_reloc_ctx *ctx, u16 insn, unsigned long old_pc,
                                      unsigned long new_pc, u16 *out)
{
    if ((insn & 0xf000) == 0xd000 && (insn & 0x0f00) < 0x0e00) {
        unsigned long dst = old_pc + 4 + ksu_arm32_sign_extend((insn & 0xff) << 1, 9);
        unsigned long veneer;
        int ret;

        dst = ksu_arm32_map_clone_addr(ctx, dst);
        ret = ksu_arm32_encode_thumb16_branch(insn, new_pc, dst, out);
        if (!ret)
            return 0;
        ret = ksu_arm32_emit_branch_veneer(ctx, dst | 1UL, &veneer);
        if (ret)
            return ret;
        return ksu_arm32_encode_thumb16_branch(insn, new_pc, veneer, out);
    }

    if ((insn & 0xf800) == 0xe000) {
        unsigned long dst = old_pc + 4 + ksu_arm32_sign_extend((insn & 0x07ff) << 1, 12);
        unsigned long veneer;
        int ret;

        dst = ksu_arm32_map_clone_addr(ctx, dst);
        ret = ksu_arm32_encode_thumb16_branch(insn, new_pc, dst, out);
        if (!ret)
            return 0;
        ret = ksu_arm32_emit_branch_veneer(ctx, dst | 1UL, &veneer);
        if (ret)
            return ret;
        return ksu_arm32_encode_thumb16_branch(insn, new_pc, veneer, out);
    }

    if ((insn & 0xf500) == 0xb100) {
        unsigned long dst = old_pc + 4 + (((insn >> 9) & 1) << 6) + (((insn >> 3) & 0x1f) << 1);
        unsigned long veneer;
        int ret;

        dst = ksu_arm32_map_clone_addr(ctx, dst);
        ret = ksu_arm32_encode_thumb_cbz(insn, new_pc, dst, out);
        if (!ret)
            return 0;
        ret = ksu_arm32_emit_branch_veneer(ctx, dst | 1UL, &veneer);
        if (ret)
            return ret;
        return ksu_arm32_encode_thumb_cbz(insn, new_pc, veneer, out);
    }

    if ((insn & 0xf800) == 0x4800)
        return ksu_arm32_emit_thumb_literal_veneer(ctx, insn, old_pc, new_pc, out);

    if ((insn & 0xf800) == 0xa000)
        return ksu_arm32_emit_thumb_adr_veneer(ctx, insn, old_pc, new_pc, out);

    if ((insn & 0xff00) == 0xbf00 && (insn & 0x000f))
        return -EOPNOTSUPP;

    if ((insn & 0xfc00) == 0x4400) {
        u32 op = (insn >> 8) & 0x3;
        u32 rm = (insn >> 3) & 0xf;
        u32 rd = (insn & 0x7) | ((insn >> 4) & 0x8);

        if (rm == 0xf || (op != 0x3 && rd == 0xf))
            return -EOPNOTSUPP;
    }

    *out = insn;
    return 0;
}

static int ksu_arm32_relocate_thumb32(struct ksu_arm32_reloc_ctx *ctx, u32 insn, unsigned long old_pc,
                                      unsigned long new_pc, u32 *out)
{
    if ((insn & 0xf800d000) == 0xf0008000)
        return -EOPNOTSUPP;

    if ((insn & 0xf8009000) == 0xf0009000) {
        u16 first = __opcode_thumb32_first(insn);
        u16 second = __opcode_thumb32_second(insn);
        u32 s = (first >> 10) & 1;
        u32 j1 = (second >> 13) & 1;
        u32 j2 = (second >> 11) & 1;
        u32 i1 = !(j1 ^ s);
        u32 i2 = !(j2 ^ s);
        u32 imm = (s << 24) | (i1 << 23) | (i2 << 22) | ((first & 0x03ff) << 12) | ((second & 0x07ff) << 1);
        unsigned long dst = old_pc + 4 + ksu_arm32_sign_extend(imm, 25);
        unsigned long veneer;
        bool link = second & BIT(14);
        int ret;

        dst = ksu_arm32_map_clone_addr(ctx, dst);
        ret = ksu_arm32_encode_thumb_branch(link, new_pc, dst, out);
        if (!ret)
            return 0;
        ret = ksu_arm32_emit_branch_veneer(ctx, dst | 1UL, &veneer);
        if (ret)
            return ret;
        return ksu_arm32_encode_thumb_branch(link, new_pc, veneer, out);
    }

    if ((insn & 0xf800d001) == 0xf000c000)
        return -EOPNOTSUPP;

    if ((insn & 0xfc000000) == 0xec000000)
        return -EOPNOTSUPP;

    if ((insn & 0xff7f0000) == 0xf85f0000 || (insn & 0xfe5f0000) == 0xf81f0000)
        return -EOPNOTSUPP;

    if ((insn & 0xfbff8000) == 0xf20f0000 || (insn & 0xfbff8000) == 0xf2af0000)
        return -EOPNOTSUPP;

    if ((insn & 0xfff000e0) == 0xe8d00000)
        return -EOPNOTSUPP;

    if ((insn & 0xfe000000) == 0xf8000000 && ((insn >> 16) & 0xf) == 0xf)
        return -EOPNOTSUPP;

    if (((insn & 0xfe400000) == 0xe8000000 || (insn & 0xfe400000) == 0xe8400000) && ((insn >> 16) & 0xf) == 0xf)
        return -EOPNOTSUPP;

    if ((insn & 0xfe000000) == 0xea000000) {
        u32 rn = (insn >> 16) & 0xf;
        u32 rd = (insn >> 8) & 0xf;
        u32 rm = insn & 0xf;
        bool mov = (insn & 0xffcf0000) == 0xea4f0000;

        if (rm == 0xf || rd == 0xf || (!mov && rn == 0xf))
            return -EOPNOTSUPP;
    }

    *out = insn;
    return 0;
}
#endif /* CONFIG_THUMB2_KERNEL */

static int ksu_arm32_build_reinsn(struct ksu_inline_hook *hook, unsigned long clone, unsigned long *veneer_cursor)
{
    struct ksu_arm32_reloc_ctx ctx = {
        .old_base = (unsigned long)hook->target,
        .new_base = clone,
        .size = hook->patch_size,
        .veneer_cursor = clone + hook->patch_size + KSU_ARM32_ABS_BRANCH_SIZE,
        .veneer_end = (unsigned long)hook->code + hook->code_size - KSU_ARM32_ENTRY_SIZE - 3,
    };
    size_t offset = 0;
    int ret;

#ifdef CONFIG_THUMB2_KERNEL
    while (offset < hook->patch_size) {
        u16 first = ksu_arm32_read_thumb16(hook->orig + offset);

        if (ksu_arm32_thumb_is_wide(first)) {
            u32 insn;
            u32 relocated;

            if (offset + sizeof(insn) > hook->patch_size)
                return -EINVAL;
            insn = ksu_arm32_read_thumb32(hook->orig + offset);
            ret = ksu_arm32_relocate_thumb32(&ctx, insn, (unsigned long)hook->target + offset, clone + offset,
                                             &relocated);
            if (ret) {
                pr_err("inline_hook: thumb reinsn failed target=%px off=0x%zx insn=%08x ret=%d\n", hook->target, offset,
                       insn, ret);
                return ret;
            }
            ksu_arm32_write_thumb32((void *)(clone + offset), relocated);
            offset += sizeof(insn);
        } else {
            u16 relocated;

            ret = ksu_arm32_relocate_thumb16(&ctx, first, (unsigned long)hook->target + offset, clone + offset,
                                             &relocated);
            if (ret) {
                pr_err("inline_hook: thumb reinsn failed target=%px off=0x%zx insn=%04x ret=%d\n", hook->target, offset,
                       first, ret);
                return ret;
            }
            ksu_arm32_write_thumb16((void *)(clone + offset), relocated);
            offset += sizeof(first);
        }
    }
#else
    while (offset < hook->patch_size) {
        u32 insn = ksu_arm32_read_arm(hook->orig + offset);
        u32 relocated;

        ret = ksu_arm32_relocate_arm_insn(&ctx, insn, (unsigned long)hook->target + offset, clone + offset, &relocated);
        if (ret) {
            pr_err("inline_hook: arm reinsn failed target=%px off=0x%zx insn=%08x ret=%d\n", hook->target, offset, insn,
                   ret);
            return ret;
        }
        ksu_arm32_write_arm((void *)(clone + offset), relocated);
        offset += sizeof(insn);
    }
#endif

    ret = ksu_arm32_make_direct_branch(clone + hook->patch_size, (unsigned long)hook->target + hook->patch_size,
                                       (u8 *)(clone + hook->patch_size), KSU_ARM32_ABS_BRANCH_SIZE);
    if (ret) {
        ret = ksu_inline_hook_arch_make_branch(ksu_arm32_code_ptr((u8 *)hook->target + hook->patch_size),
                                               (u8 *)(clone + hook->patch_size), KSU_ARM32_ABS_BRANCH_SIZE);
        if (ret)
            return ret;
    }

    *veneer_cursor = ctx.veneer_cursor;
    return 0;
}

static int ksu_arm32_write_arm_ldr_literal(void *buf, size_t offset, u32 reg, size_t literal_offset, u32 condition)
{
    s64 diff = (s64)((unsigned long)buf + literal_offset) - (s64)((unsigned long)buf + offset + 8);
    u32 imm;
    u32 opcode;

    if (diff < -0xfff || diff > 0xfff)
        return -ERANGE;

    imm = diff < 0 ? -diff : diff;
    opcode = ((condition & 0xf) << 28) | (diff < 0 ? 0x051f0000 : 0x059f0000) | ((reg & 0xf) << 12) | imm;
    ksu_arm32_write_arm((u8 *)buf + offset, opcode);
    return 0;
}

static int ksu_arm32_write_thumb_ldr_literal(void *buf, size_t offset, u32 reg, size_t literal_offset)
{
    unsigned long pc = ((unsigned long)buf + offset + 4) & ~(sizeof(u32) - 1);
    s64 diff = (s64)((unsigned long)buf + literal_offset) - (s64)pc;
    u32 opcode;

    if (diff < 0 || diff > 0xfff)
        return -ERANGE;

    opcode = 0xf8df0000 | ((reg & 0xf) << 12) | ((u32)diff & 0xfff);
    ksu_arm32_write_thumb32((u8 *)buf + offset, opcode);
    return 0;
}

static int ksu_arm32_make_entry_stub(struct ksu_inline_hook *hook, void *buf)
{
    size_t offset = 0;
    u32 hook_addr = (u32)hook;
    u32 clone_addr = (u32)hook->clone;
    u32 current_addr = 0;
    u32 entry_addr;
    int ret;

    if (hook->before && hook->after)
        entry_addr = (u32)ksu_inline_hook_arm32_entry_with_before_and_after;
    else if (hook->before)
        entry_addr = (u32)ksu_inline_hook_arm32_entry_with_before;
    else
        entry_addr = (u32)ksu_inline_hook_arm32_entry_with_after;

    memset(buf, 0, KSU_ARM32_ENTRY_SIZE);
    BUILD_BUG_ON(TIF_PROC_NON_PRIVILEGE != 30);
    if ((unsigned long)buf & (sizeof(u32) - 1))
        return -EINVAL;

#ifdef CONFIG_THUMB2_KERNEL
#ifdef CONFIG_THREAD_INFO_IN_TASK
#if defined(CONFIG_CURRENT_POINTER_IN_TPIDRURO) || defined(CONFIG_SMP)
    /* mrc p15, 0, ip, c13, c0, 3 */
    ksu_arm32_write_thumb32((u8 *)buf + offset, 0xee1dcf70);
    offset += sizeof(u32);
#else
    current_addr = (u32)&__current;
    ret = ksu_arm32_write_thumb_ldr_literal(buf, offset, 12, KSU_ARM32_ENTRY_CURRENT_LITERAL);
    if (ret)
        return ret;
    offset += sizeof(u32);
    ksu_arm32_write_thumb32((u8 *)buf + offset, 0xf8dcc000); /* ldr.w ip, [ip] */
    offset += sizeof(u32);
#endif
#else
    /*
     * mov ip, sp; lsr.w ip, ip, #log2(THREAD_SIZE);
     * lsl.w ip, ip, #log2(THREAD_SIZE)
     */
    ksu_arm32_write_thumb16((u8 *)buf + offset, 0x46ec);
    offset += sizeof(u16);
    ksu_arm32_write_thumb32((u8 *)buf + offset,
                            ksu_arm32_encode_thumb_shift_imm(1, 12, 12, THREAD_SIZE_ORDER + PAGE_SHIFT));
    offset += sizeof(u32);
    ksu_arm32_write_thumb32((u8 *)buf + offset,
                            ksu_arm32_encode_thumb_shift_imm(0, 12, 12, THREAD_SIZE_ORDER + PAGE_SHIFT));
    offset += sizeof(u32);
#endif

    ksu_arm32_write_thumb32((u8 *)buf + offset, 0xf8dcc000); /* ldr.w ip, [ip]: thread flags */
    offset += sizeof(u32);

    /* tst.w ip, #BIT(30); it ne; ldrne.w pc, .Lclone */
    ksu_arm32_write_thumb32((u8 *)buf + offset, 0xf01c4f80);
    offset += sizeof(u32);
    ksu_arm32_write_thumb16((u8 *)buf + offset, 0xbf18);
    offset += sizeof(u16);
    ret = ksu_arm32_write_thumb_ldr_literal(buf, offset, 15, KSU_ARM32_ENTRY_CLONE_LITERAL);
    if (ret)
        return ret;
    offset += sizeof(u32);

    /*
     * Only the slow path reaches this push. ip and APSR are caller-saved;
     * r0-r3, lr and sp remain untouched on the direct-to-clone fast path.
     */
    ksu_arm32_write_thumb16((u8 *)buf + offset, 0xb500); /* push {lr} */
    offset += sizeof(u16);
    ret = ksu_arm32_write_thumb_ldr_literal(buf, offset, 12, KSU_ARM32_ENTRY_HOOK_LITERAL);
    if (ret)
        return ret;
    offset += sizeof(u32);
    ret = ksu_arm32_write_thumb_ldr_literal(buf, offset, 14, KSU_ARM32_ENTRY_CLONE_LITERAL);
    if (ret)
        return ret;
    offset += sizeof(u32);
    ret = ksu_arm32_write_thumb_ldr_literal(buf, offset, 15, KSU_ARM32_ENTRY_DISPATCH_LITERAL);
    if (ret)
        return ret;
    offset += sizeof(u32);

#else
#ifdef CONFIG_THREAD_INFO_IN_TASK
#if defined(CONFIG_CURRENT_POINTER_IN_TPIDRURO) || defined(CONFIG_SMP)
    ksu_arm32_write_arm((u8 *)buf + offset, 0xee1dcf70); /* mrc p15, 0, ip, c13, c0, 3 */
    offset += sizeof(u32);
#else
    current_addr = (u32)&__current;
    ret = ksu_arm32_write_arm_ldr_literal(buf, offset, 12, KSU_ARM32_ENTRY_CURRENT_LITERAL, 0xe);
    if (ret)
        return ret;
    offset += sizeof(u32);
    ksu_arm32_write_arm((u8 *)buf + offset, 0xe59cc000); /* ldr ip, [ip] */
    offset += sizeof(u32);
#endif
#else
    ksu_arm32_write_arm((u8 *)buf + offset,
                        0xe1a0c02d | ((THREAD_SIZE_ORDER + PAGE_SHIFT) << 7)); /* lsr ip, sp, #shift */
    offset += sizeof(u32);
    ksu_arm32_write_arm((u8 *)buf + offset,
                        0xe1a0c00c | ((THREAD_SIZE_ORDER + PAGE_SHIFT) << 7)); /* lsl ip, ip, #shift */
    offset += sizeof(u32);
#endif

    ksu_arm32_write_arm((u8 *)buf + offset, 0xe59cc000); /* ldr ip, [ip]: thread flags */
    offset += sizeof(u32);

    ksu_arm32_write_arm((u8 *)buf + offset, 0xe31c0101); /* tst ip, #BIT(30) */
    offset += sizeof(u32);
    ret = ksu_arm32_write_arm_ldr_literal(buf, offset, 15, KSU_ARM32_ENTRY_CLONE_LITERAL, 0x1);
    if (ret)
        return ret;
    offset += sizeof(u32);

    /* Only the slow path saves LR and enters the full context trampoline. */
    ksu_arm32_write_arm((u8 *)buf + offset, 0xe52de004); /* push {lr} */
    offset += sizeof(u32);
    ret = ksu_arm32_write_arm_ldr_literal(buf, offset, 12, KSU_ARM32_ENTRY_HOOK_LITERAL, 0xe);
    if (ret)
        return ret;
    offset += sizeof(u32);
    ret = ksu_arm32_write_arm_ldr_literal(buf, offset, 14, KSU_ARM32_ENTRY_CLONE_LITERAL, 0xe);
    if (ret)
        return ret;
    offset += sizeof(u32);
    ret = ksu_arm32_write_arm_ldr_literal(buf, offset, 15, KSU_ARM32_ENTRY_DISPATCH_LITERAL, 0xe);
    if (ret)
        return ret;
    offset += sizeof(u32);
#endif

    if (offset > KSU_ARM32_ENTRY_LITERAL_OFFSET)
        return -ENOSPC;
#ifdef CONFIG_THUMB2_KERNEL
    while (offset < KSU_ARM32_ENTRY_LITERAL_OFFSET) {
        ksu_arm32_write_thumb16((u8 *)buf + offset, KSU_ARM32_THUMB_NOP);
        offset += sizeof(u16);
    }
#else
    while (offset < KSU_ARM32_ENTRY_LITERAL_OFFSET) {
        ksu_arm32_write_arm((u8 *)buf + offset, KSU_ARM32_ARM_NOP);
        offset += sizeof(u32);
    }
#endif

    memcpy((u8 *)buf + KSU_ARM32_ENTRY_HOOK_LITERAL, &hook_addr, sizeof(hook_addr));
    memcpy((u8 *)buf + KSU_ARM32_ENTRY_CLONE_LITERAL, &clone_addr, sizeof(clone_addr));
    memcpy((u8 *)buf + KSU_ARM32_ENTRY_DISPATCH_LITERAL, &entry_addr, sizeof(entry_addr));
    memcpy((u8 *)buf + KSU_ARM32_ENTRY_CURRENT_LITERAL, &current_addr, sizeof(current_addr));

    return 0;
}

static void ksu_arm32_dump_target(const char *reason, struct ksu_inline_hook *hook, unsigned long size)
{
    unsigned long dump_size = min_t(unsigned long, size ?: hook->patch_size, 24);
    unsigned long offset;

    pr_err("inline_hook: %s target=%px (%pS) size=%lu patch=%zu\n", reason, hook->target, hook->target, size,
           hook->patch_size);
    for (offset = 0; offset + sizeof(u16) <= dump_size; offset += sizeof(u16))
        pr_err("inline_hook: arm target+0x%02lx halfword=%04x\n", offset,
               ksu_arm32_read_thumb16((u8 *)hook->target + offset));
}

static int ksu_arm32_check_target(struct ksu_inline_hook *hook)
{
    unsigned long size = 0;
    unsigned long alignment;

#ifdef CONFIG_THUMB2_KERNEL
    alignment = sizeof(u16);
#else
    alignment = sizeof(u32);
#endif

    if ((unsigned long)hook->target & (alignment - 1)) {
        ksu_arm32_dump_target("unaligned arm target", hook, size);
        return -EINVAL;
    }
    if (!kallsyms_lookup_size_offset((unsigned long)hook->target, &size, NULL)) {
        ksu_arm32_dump_target("arm target size lookup failed", hook, size);
        return -EOPNOTSUPP;
    }
    if (size < hook->patch_size || hook->patch_size < KSU_ARM32_DIRECT_BRANCH_SIZE ||
        (hook->patch_size & (alignment - 1))) {
        ksu_arm32_dump_target("unsupported arm target size", hook, size);
        return -EOPNOTSUPP;
    }

    return 0;
}

int ksu_inline_hook_arch_prepare(struct ksu_inline_hook *hook, u8 *patch, size_t patch_size)
{
    unsigned long veneer_budget;
    unsigned long veneer_cursor;
    size_t code_size;
    void *entry_raw;
    void *entry;
    void *clone_raw;
    void *code;
    int ret;

    if (!patch_size || patch_size != hook->patch_size || patch_size > KSU_INLINE_MAX_PATCH_SIZE)
        return -EINVAL;

    ret = ksu_arm32_check_target(hook);
    if (ret)
        return ret;

    veneer_budget = (hook->patch_size / sizeof(u16)) * KSU_ARM32_MAX_VENEER_SIZE;
    code_size = PAGE_ALIGN(((unsigned long)hook->target & ~PAGE_MASK) + hook->patch_size + KSU_ARM32_ABS_BRANCH_SIZE +
                           veneer_budget + KSU_ARM32_ENTRY_SIZE + 3);
    code = ksu_inline_hook_code_alloc(hook->target, code_size, patch_size < KSU_ARM32_ABS_BRANCH_SIZE);
    if (!code)
        return -ENOMEM;

    hook->keep_storage = true;
    hook->code = code;
    hook->code_size = code_size;

    clone_raw = (u8 *)code + ((unsigned long)hook->target & ~PAGE_MASK);
    hook->clone = ksu_arm32_code_ptr(clone_raw);

    ret = ksu_arm32_build_reinsn(hook, (unsigned long)clone_raw, &veneer_cursor);
    if (ret)
        goto err_free;

    entry_raw = PTR_ALIGN((void *)veneer_cursor, sizeof(u32));
    entry = ksu_arm32_code_ptr(entry_raw);
    ret = ksu_arm32_make_entry_stub(hook, entry_raw);
    if (ret)
        goto err_free;
    hook->trampoline = entry;

    flush_icache_range((unsigned long)code, (unsigned long)code + code_size);

    ret = ksu_arm32_make_direct_branch((unsigned long)hook->target, (unsigned long)entry_raw, patch, patch_size);
    if (ret) {
        if (patch_size < KSU_ARM32_ABS_BRANCH_SIZE)
            goto err_free;
        ret = ksu_inline_hook_arch_make_branch(entry, patch, patch_size);
        if (ret)
            goto err_free;
    }

    pr_info("inline_hook: prepared arm32 target=%px mode=reinsn patch=%zu trampoline=%px clone=%px\n", hook->target,
            hook->patch_size, hook->trampoline, hook->clone);
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
        ksu_inline_code_free(hook->code);
        hook->code = NULL;
        hook->code_size = 0;
        hook->trampoline = NULL;
        hook->clone = NULL;
    }

    hook->slot = KSU_INLINE_INVALID_SLOT;
}

#endif /* __arm__ */
