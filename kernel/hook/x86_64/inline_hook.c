/* SPDX-License-Identifier: GPL-2.0-only */

#ifdef __x86_64__

#include "hook/inline_hook_internal.h"

#include <linux/errno.h>
#include <linux/gfp.h>
#include <linux/kallsyms.h>
#include <linux/mm.h>
#include <linux/mutex.h>
#include <linux/numa.h>
#include <linux/random.h>
#include <linux/string.h>
#include <linux/vmalloc.h>
#include <linux/version.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 0, 0)
#include <linux/kasan.h>
#endif
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 10, 0)
#include <linux/execmem.h>
#endif

#include <asm/insn.h>
#include <asm/current.h>
#include <asm/pgtable.h>
#include <asm/setup.h>

#include "compat/kernel_compat.h"
#include "feature/sucompat.h"
#include "infra/symbol_resolver.h"

#define KSU_X86_64_ABS_BRANCH_SIZE 14
#define KSU_X86_64_DIRECT_BRANCH_SIZE 5
#define KSU_X86_64_ENDBR_SIZE 4
#define KSU_X86_64_ENTRY_SIZE 80
#define KSU_X86_64_MAX_INSN_SIZE 15
#define KSU_X86_64_VENEER_SIZE 5

#if defined(CONFIG_KASAN) && LINUX_VERSION_CODE >= KERNEL_VERSION(4, 0, 0)
#define KSU_X86_64_MODULE_ALIGN (PAGE_SIZE << KASAN_SHADOW_SCALE_SHIFT)
#else
#define KSU_X86_64_MODULE_ALIGN PAGE_SIZE
#endif

extern void ksu_inline_hook_x86_64_entry_with_after(void);
extern void ksu_inline_hook_x86_64_entry_with_before(void);
extern void ksu_inline_hook_x86_64_entry_with_before_and_after(void);

typedef void (*ksu_x86_insn_get_length_t)(struct insn *insn);

static ksu_x86_insn_get_length_t ksu_x86_insn_get_length;

static inline bool ksu_x86_simm_fits(s64 value, unsigned int bytes)
{
    switch (bytes) {
    case 1:
        return value >= -(1LL << 7) && value <= (1LL << 7) - 1;
    case 2:
        return value >= -(1LL << 15) && value <= (1LL << 15) - 1;
    case 4:
        return value >= -(1LL << 31) && value <= (1LL << 31) - 1;
    default:
        return false;
    }
}

static s64 ksu_x86_read_simm(const u8 *p, unsigned int bytes)
{
    s8 value8;
    s16 value16;
    s32 value32;

    switch (bytes) {
    case 1:
        memcpy(&value8, p, sizeof(value8));
        return value8;
    case 2:
        memcpy(&value16, p, sizeof(value16));
        return value16;
    case 4:
        memcpy(&value32, p, sizeof(value32));
        return value32;
    default:
        return 0;
    }
}

static void ksu_x86_write_simm(u8 *p, s64 value, unsigned int bytes)
{
    s8 value8 = value;
    s16 value16 = value;
    s32 value32 = value;

    switch (bytes) {
    case 1:
        memcpy(p, &value8, sizeof(value8));
        break;
    case 2:
        memcpy(p, &value16, sizeof(value16));
        break;
    case 4:
        memcpy(p, &value32, sizeof(value32));
        break;
    }
}

static int __nocfi ksu_x86_decode_insn(const void *addr, size_t available, struct insn *insn)
{
    size_t decode_size = min_t(size_t, available, KSU_X86_64_MAX_INSN_SIZE);

    if (!ksu_x86_insn_get_length) {
        ksu_x86_insn_get_length = (ksu_x86_insn_get_length_t)find_kernel_symbol_exact("insn_get_length");
        if (!ksu_x86_insn_get_length) {
            pr_err_once("inline_hook: x86 instruction decoder is unavailable\n");
            return -EOPNOTSUPP;
        }
    }

    memset(insn, 0, sizeof(*insn));
    insn->kaddr = addr;
#ifdef KSU_COMPAT_X86_INSN_HAS_END_KADDR
    insn->end_kaddr = (const insn_byte_t *)addr + decode_size;
#endif
    insn->next_byte = addr;
    insn->x86_64 = 1;
    insn->opnd_bytes = 4;
    insn->addr_bytes = 8;

    ksu_x86_insn_get_length(insn);
    if (!insn->length || insn->length > decode_size || !insn->opcode.got || !insn->modrm.got || !insn->sib.got ||
        !insn->displacement.got || !insn->immediate.got)
        return -EINVAL;
#ifdef KSU_COMPAT_X86_INSN_HAS_EMULATE_PREFIX
    if (insn->emulate_prefix_size)
        return -EOPNOTSUPP;
#endif

    return 0;
}

static inline bool ksu_x86_has_endbr(const void *addr)
{
    static const u8 endbr64[] = { 0xf3, 0x0f, 0x1e, 0xfa };

    return !memcmp(addr, endbr64, sizeof(endbr64));
}

static int ksu_x86_make_rel32_branch(u8 opcode, unsigned long from, unsigned long to, u8 *out)
{
    s64 diff = (s64)to - (s64)(from + KSU_X86_64_DIRECT_BRANCH_SIZE);
    s32 rel;

    if (!ksu_x86_simm_fits(diff, sizeof(rel)))
        return -ERANGE;

    rel = diff;
    out[0] = opcode;
    memcpy(out + 1, &rel, sizeof(rel));
    return 0;
}

static int ksu_x86_make_abs_branch(void *to, u8 *patch, size_t patch_size)
{
    u64 addr = (u64)to;

    if (patch_size < KSU_X86_64_ABS_BRANCH_SIZE)
        return -EINVAL;

    memset(patch, 0x90, patch_size);
    patch[0] = 0xff;
    patch[1] = 0x25;
    memset(patch + 2, 0, sizeof(u32));
    memcpy(patch + 6, &addr, sizeof(addr));
    return 0;
}

void *ksu_inline_hook_arch_normalize_target(void *target)
{
    return target;
}

size_t ksu_inline_hook_arch_patch_size(void *target)
{
    unsigned long target_size = 0;
    size_t direct_minimum;
    size_t preferred_minimum;
    size_t minimum;
    size_t size = 0;
    int ret;

    if (!target)
        return 0;

    direct_minimum = KSU_X86_64_DIRECT_BRANCH_SIZE;
    preferred_minimum = KSU_X86_64_ABS_BRANCH_SIZE;
    if (ksu_x86_has_endbr(target)) {
        direct_minimum += KSU_X86_64_ENDBR_SIZE;
        preferred_minimum += KSU_X86_64_ENDBR_SIZE;
    }

    if (!kallsyms_lookup_size_offset((unsigned long)target, &target_size, NULL) || target_size < direct_minimum) {
        pr_err("inline_hook: unsupported x86 target=%px (%pS), size=%lu minimum=%zu\n", target, target, target_size,
               direct_minimum);
        return 0;
    }

    /* Prefer the no-clobber absolute form, but keep short thunks hookable. */
    minimum = target_size >= preferred_minimum ? preferred_minimum : direct_minimum;

    while (size < minimum) {
        struct insn insn;

        ret = ksu_x86_decode_insn((u8 *)target + size, target_size - size, &insn);
        if (ret) {
            pr_err("inline_hook: failed to decode x86 target=%px off=0x%zx: %d\n", target, size, ret);
            return 0;
        }

        if (size + insn.length > KSU_INLINE_MAX_PATCH_SIZE) {
            pr_err("inline_hook: x86 prologue exceeds patch buffer target=%px size=%zu next=%u\n", target, size,
                   insn.length);
            return 0;
        }
        size += insn.length;
    }

    if (((unsigned long)target & ~PAGE_MASK) + size > PAGE_SIZE) {
        pr_err("inline_hook: x86 prologue crosses a page target=%px patch=%zu\n", target, size);
        return 0;
    }

    return size;
}

int ksu_inline_hook_arch_make_branch(void *to, u8 *patch, size_t patch_size)
{
    return ksu_x86_make_abs_branch(to, patch, patch_size);
}

unsigned long ksu_inline_hook_arch_get_ret(const struct pt_regs *regs)
{
    return regs->ax;
}

void ksu_inline_hook_arch_setup_regs(struct pt_regs *regs, unsigned long *arg_regs)
{
    if (!arg_regs)
        return;

    regs->di = arg_regs[0];
    regs->si = arg_regs[1];
    regs->dx = arg_regs[2];
    regs->cx = arg_regs[3];
    regs->r10 = arg_regs[3];
    regs->r8 = arg_regs[4];
    regs->r9 = arg_regs[5];
    regs->sp = arg_regs[6];
}

void ksu_inline_hook_arch_update_args(const struct pt_regs *regs, unsigned long *arg_regs)
{
    if (!arg_regs)
        return;

    arg_regs[0] = regs->di;
    arg_regs[1] = regs->si;
    arg_regs[2] = regs->dx;
    if (regs->r10 != arg_regs[3])
        arg_regs[3] = regs->r10;
    else
        arg_regs[3] = regs->cx;
    arg_regs[4] = regs->r8;
    arg_regs[5] = regs->r9;
    arg_regs[6] = regs->sp;
}

void ksu_inline_hook_arch_set_ret(struct pt_regs *regs, unsigned long ret)
{
    regs->ax = ret;
}

#ifdef CONFIG_RANDOMIZE_BASE
static unsigned long ksu_inline_module_load_offset;
static DEFINE_MUTEX(ksu_inline_module_kaslr_mutex);

static unsigned long ksu_inline_get_module_load_offset(void)
{
    if (kaslr_enabled()) {
        mutex_lock(&ksu_inline_module_kaslr_mutex);
        if (!ksu_inline_module_load_offset)
            ksu_inline_module_load_offset = (get_random_int() % 1024 + 1) * PAGE_SIZE;
        mutex_unlock(&ksu_inline_module_kaslr_mutex);
    }

    return ksu_inline_module_load_offset;
}
#else
static unsigned long ksu_inline_get_module_load_offset(void)
{
    return 0;
}
#endif

static inline int ksu_inline_kasan_module_alloc(void *p, size_t size)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 0, 0)
    return 0;
#else
#ifdef KSU_COMPAT_HAVE_KASAN_ALLOC_MODULE_SHADOW
    return kasan_alloc_module_shadow(p, size, GFP_KERNEL);
#else
    return kasan_module_alloc(p, size);
#endif
#endif
}

static void *ksu_inline_hook_code_alloc(size_t size)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0) && !defined(KSU_COMPAT_HAVE_EXECMEM_API)
    void *p;

    if (PAGE_ALIGN(size) > MODULES_LEN)
        return NULL;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 0, 0) || defined(KSU_COMPAT_HAVE_VMFLAGS_IN_VMALLOC_NODE_RANGE)
    p = __vmalloc_node_range(size, KSU_X86_64_MODULE_ALIGN, MODULES_VADDR + ksu_inline_get_module_load_offset(),
                             MODULES_END, GFP_KERNEL, PAGE_KERNEL_EXEC, 0, NUMA_NO_NODE, __builtin_return_address(0));
#else
    p = __vmalloc_node_range(size, KSU_X86_64_MODULE_ALIGN, MODULES_VADDR + ksu_inline_get_module_load_offset(),
                             MODULES_END, GFP_KERNEL, PAGE_KERNEL_EXEC, NUMA_NO_NODE, __builtin_return_address(0));
#endif
    if (p && ksu_inline_kasan_module_alloc(p, size) < 0) {
        vfree(p);
        return NULL;
    }

    return p;
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

struct ksu_x86_reloc_ctx {
    unsigned long old_base;
    unsigned long new_base;
    unsigned long size;
    unsigned long veneer_cursor;
    unsigned long veneer_end;
};

static inline unsigned long ksu_x86_map_clone_addr(const struct ksu_x86_reloc_ctx *ctx, unsigned long addr)
{
    if (addr >= ctx->old_base && addr < ctx->old_base + ctx->size)
        return ctx->new_base + (addr - ctx->old_base);

    return addr;
}

static int ksu_x86_emit_veneer(struct ksu_x86_reloc_ctx *ctx, unsigned long dst, unsigned long *veneer_addr)
{
    u8 *veneer = (u8 *)ctx->veneer_cursor;
    int ret;

    if ((unsigned long)veneer + KSU_X86_64_VENEER_SIZE > ctx->veneer_end)
        return -ENOSPC;

    ret = ksu_x86_make_rel32_branch(0xe9, (unsigned long)veneer, dst, veneer);
    if (ret)
        return ret;

    *veneer_addr = (unsigned long)veneer;
    ctx->veneer_cursor += KSU_X86_64_VENEER_SIZE;
    return 0;
}

static bool ksu_x86_is_relative_branch(const struct insn *insn)
{
    u8 op0 = insn->opcode.bytes[0];
    u8 op1 = insn->opcode.bytes[1];

    if (op0 == 0xe8 || op0 == 0xe9 || op0 == 0xeb)
        return true;
    if (op0 >= 0x70 && op0 <= 0x7f)
        return true;
    if (op0 >= 0xe0 && op0 <= 0xe3)
        return true;
    if (op0 == 0x0f && op1 >= 0x80 && op1 <= 0x8f)
        return true;

    return false;
}

static int ksu_x86_relocate_branch(struct ksu_x86_reloc_ctx *ctx, const struct insn *insn, unsigned long old_pc,
                                   unsigned long new_pc, u8 *out)
{
    unsigned int immediate_offset = insn_offset_immediate((struct insn *)insn);
    unsigned int immediate_size = insn->immediate.nbytes;
    unsigned long dst;
    unsigned long veneer;
    s64 old_disp;
    s64 new_disp;
    int ret;

    if (!immediate_size || immediate_offset + immediate_size > insn->length ||
        (immediate_size != 1 && immediate_size != 2 && immediate_size != 4))
        return -EOPNOTSUPP;

    old_disp = ksu_x86_read_simm((const u8 *)insn->kaddr + immediate_offset, immediate_size);
    dst = old_pc + insn->length + old_disp;
    dst = ksu_x86_map_clone_addr(ctx, dst);
    new_disp = (s64)dst - (s64)(new_pc + insn->length);

    if (!ksu_x86_simm_fits(new_disp, immediate_size)) {
        if (immediate_size == 4)
            return -ERANGE;

        ret = ksu_x86_emit_veneer(ctx, dst, &veneer);
        if (ret)
            return ret;

        new_disp = (s64)veneer - (s64)(new_pc + insn->length);
        if (!ksu_x86_simm_fits(new_disp, immediate_size))
            return -ERANGE;
    }

    ksu_x86_write_simm(out + immediate_offset, new_disp, immediate_size);
    return 0;
}

static int ksu_x86_relocate_rip_relative(const struct ksu_x86_reloc_ctx *ctx, const struct insn *insn,
                                         unsigned long old_pc, unsigned long new_pc, u8 *out)
{
    unsigned int displacement_offset = insn_offset_displacement((struct insn *)insn);
    unsigned int displacement_size = insn->displacement.nbytes;
    unsigned long dst;
    s64 old_disp;
    s64 new_disp;

    if (displacement_size != sizeof(s32) || displacement_offset + displacement_size > insn->length)
        return -EOPNOTSUPP;

    old_disp = ksu_x86_read_simm((const u8 *)insn->kaddr + displacement_offset, displacement_size);
    dst = old_pc + insn->length + old_disp;
    dst = ksu_x86_map_clone_addr(ctx, dst);
    new_disp = (s64)dst - (s64)(new_pc + insn->length);
    if (!ksu_x86_simm_fits(new_disp, sizeof(s32)))
        return -ERANGE;

    ksu_x86_write_simm(out + displacement_offset, new_disp, sizeof(s32));
    return 0;
}

static int ksu_x86_relocate_insn(struct ksu_x86_reloc_ctx *ctx, const struct insn *insn, unsigned long old_pc,
                                 unsigned long new_pc, u8 *out)
{
    bool rip_relative;

    memcpy(out, insn->kaddr, insn->length);

    /* XBEGIN carries a relative immediate but is not a normal branch. */
    if (insn->opcode.bytes[0] == 0xc7 && insn->modrm.nbytes && insn->modrm.bytes[0] == 0xf8)
        return -EOPNOTSUPP;

    if (ksu_x86_is_relative_branch(insn))
        return ksu_x86_relocate_branch(ctx, insn, old_pc, new_pc, out);

    rip_relative = insn->addr_bytes == 8 && insn->modrm.nbytes && (insn->modrm.bytes[0] & 0xc7) == 0x05;
    if (rip_relative)
        return ksu_x86_relocate_rip_relative(ctx, insn, old_pc, new_pc, out);

    return 0;
}

static int ksu_x86_build_reinsn(struct ksu_inline_hook *hook, unsigned long clone, unsigned long *veneer_cursor)
{
    struct ksu_x86_reloc_ctx ctx = {
        .old_base = (unsigned long)hook->target,
        .new_base = clone,
        .size = hook->patch_size,
        .veneer_cursor = clone + hook->patch_size + KSU_X86_64_DIRECT_BRANCH_SIZE,
        .veneer_end = (unsigned long)hook->code + hook->code_size - KSU_X86_64_ENTRY_SIZE - 15,
    };
    size_t offset = 0;
    unsigned int insn_length = 0;
    int ret;

    while (offset < hook->patch_size) {
        struct insn insn;

        ret = ksu_x86_decode_insn(hook->orig + offset, hook->patch_size - offset, &insn);
        if (ret)
            goto err;
        insn_length = insn.length;

        ret = ksu_x86_relocate_insn(&ctx, &insn, (unsigned long)hook->target + offset, clone + offset,
                                    (u8 *)clone + offset);
        if (ret)
            goto err;

        offset += insn.length;
    }

    if (offset != hook->patch_size)
        return -EINVAL;

    ret = ksu_x86_make_rel32_branch(0xe9, clone + hook->patch_size, (unsigned long)hook->target + hook->patch_size,
                                    (u8 *)clone + hook->patch_size);
    if (ret)
        return ret;

    *veneer_cursor = ctx.veneer_cursor;
    return 0;

err:
    pr_err("inline_hook: x86 reinsn failed target=%px off=0x%zx len=%u ret=%d\n", hook->target, offset, insn_length,
           ret);
    return ret;
}

static int ksu_x86_make_entry_stub(struct ksu_inline_hook *hook, void *buf)
{
    u8 *code = buf;
    unsigned long current_task_addr;
#ifdef CONFIG_SMP
    u32 current_task_offset;
#endif
    size_t current_offset;
    size_t entry_branch_offset;
    size_t fast_branch_offset;
    size_t fast_offset;
    size_t prefix = 0;
    void *clone = hook->clone;
    void *entry;
    s64 fast_rel;
    int ret;

    if (hook->before && hook->after)
        entry = ksu_inline_hook_x86_64_entry_with_before_and_after;
    else if (hook->before)
        entry = ksu_inline_hook_x86_64_entry_with_before;
    else
        entry = ksu_inline_hook_x86_64_entry_with_after;

    memset(code, 0x90, KSU_X86_64_ENTRY_SIZE);
    BUILD_BUG_ON(TIF_PROC_NON_PRIVILEGE != 62);

#ifdef CONFIG_X86_KERNEL_IBT
    code[0] = 0xf3;
    code[1] = 0x0f;
    code[2] = 0x1e;
    code[3] = 0xfa;
    prefix = KSU_X86_64_ENDBR_SIZE;
#endif

#ifdef KSU_COMPAT_X86_CURRENT_IN_PCPU_HOT
    current_task_addr = (unsigned long)&pcpu_hot.current_task;
#else
    current_task_addr = (unsigned long)&current_task;
#endif
    current_offset = prefix;

#ifdef CONFIG_SMP
    /*
     * movq %gs:current_task, %r11
     *
     * x86 per-CPU symbols are zero based, so their link-time address is
     * the displacement from the GS per-CPU base.
     */
    if (current_task_addr > U32_MAX)
        return -ERANGE;
    current_task_offset = current_task_addr;
    code[current_offset] = 0x65;
    code[current_offset + 1] = 0x4c;
    code[current_offset + 2] = 0x8b;
    code[current_offset + 3] = 0x1c;
    code[current_offset + 4] = 0x25;
    memcpy(code + current_offset + 5, &current_task_offset, sizeof(current_task_offset));
    current_offset += 9;
#else
    /* movabsq $current_task, %r11; movq (%r11), %r11 */
    code[current_offset] = 0x49;
    code[current_offset + 1] = 0xbb;
    memcpy(code + current_offset + 2, &current_task_addr, sizeof(current_task_addr));
    code[current_offset + 10] = 0x4d;
    code[current_offset + 11] = 0x8b;
    code[current_offset + 12] = 0x1b;
    current_offset += 13;
#endif

    /* btq $TIF_PROC_NON_PRIVILEGE, (%r11); jc .Lfast */
    code[current_offset] = 0x49;
    code[current_offset + 1] = 0x0f;
    code[current_offset + 2] = 0xba;
    code[current_offset + 3] = 0x23;
    code[current_offset + 4] = TIF_PROC_NON_PRIVILEGE;
    fast_branch_offset = current_offset + 5;
    code[fast_branch_offset] = 0x72;

    current_offset = fast_branch_offset + 2;

    /* movabsq $hook, %r11 */
    code[current_offset] = 0x49;
    code[current_offset + 1] = 0xbb;
    memcpy(code + current_offset + 2, &hook, sizeof(hook));
    current_offset += 10;

    /* movabsq $clone, %r10 */
    code[current_offset] = 0x49;
    code[current_offset + 1] = 0xba;
    memcpy(code + current_offset + 2, &clone, sizeof(clone));
    current_offset += 10;

    /*
     * Reserve the maximum branch size so .Lfast has a fixed address
     * regardless of whether the dispatcher is within rel32 range.
     */
    entry_branch_offset = current_offset;
    fast_offset = entry_branch_offset + KSU_X86_64_ABS_BRANCH_SIZE;
    if (fast_offset + KSU_X86_64_ABS_BRANCH_SIZE > KSU_X86_64_ENTRY_SIZE)
        return -ENOSPC;
    fast_rel = (s64)fast_offset - (s64)(fast_branch_offset + 2);
    if (!ksu_x86_simm_fits(fast_rel, sizeof(s8)))
        return -ERANGE;
    code[fast_branch_offset + 1] = (s8)fast_rel;

    ret = ksu_x86_make_rel32_branch(0xe9, (unsigned long)code + entry_branch_offset, (unsigned long)entry,
                                    code + entry_branch_offset);
    if (ret) {
        ret = ksu_x86_make_abs_branch(entry, code + entry_branch_offset, KSU_X86_64_ABS_BRANCH_SIZE);
        if (ret)
            return ret;
    }

    /* .Lfast: jump straight to the reinsn clone without touching the stack. */
    ret = ksu_x86_make_rel32_branch(0xe9, (unsigned long)code + fast_offset, (unsigned long)clone, code + fast_offset);
    if (!ret)
        return 0;

    return ksu_x86_make_abs_branch(clone, code + fast_offset, KSU_X86_64_ENTRY_SIZE - fast_offset);
}

static int ksu_x86_make_target_patch(struct ksu_inline_hook *hook, void *entry, u8 *patch)
{
    size_t prefix = ksu_x86_has_endbr(hook->orig) ? KSU_X86_64_ENDBR_SIZE : 0;
    int ret;

    memset(patch, 0x90, hook->patch_size);
    if (prefix)
        memcpy(patch, hook->orig, prefix);

    ret = ksu_x86_make_rel32_branch(0xe9, (unsigned long)hook->target + prefix, (unsigned long)entry, patch + prefix);
    if (!ret)
        return 0;
    if (hook->patch_size - prefix < KSU_X86_64_ABS_BRANCH_SIZE)
        return ret;

    return ksu_x86_make_abs_branch(entry, patch + prefix, hook->patch_size - prefix);
}

static int ksu_x86_check_target(struct ksu_inline_hook *hook)
{
    unsigned long size;

    if (!kallsyms_lookup_size_offset((unsigned long)hook->target, &size, NULL)) {
        pr_err("inline_hook: x86 target size lookup failed target=%px (%pS)\n", hook->target, hook->target);
        return -EOPNOTSUPP;
    }
    if (size < hook->patch_size) {
        pr_err("inline_hook: x86 target too small target=%px (%pS) size=%lu patch=%zu\n", hook->target, hook->target,
               size, hook->patch_size);
        return -EOPNOTSUPP;
    }
#ifdef CONFIG_X86_KERNEL_IBT
    if (!ksu_x86_has_endbr(hook->orig)) {
        pr_err("inline_hook: x86 IBT target lacks ENDBR64 target=%px (%pS)\n", hook->target, hook->target);
        return -EOPNOTSUPP;
    }
#endif

    return 0;
}

int ksu_inline_hook_arch_prepare(struct ksu_inline_hook *hook, u8 *patch, size_t patch_size)
{
    unsigned long veneer_budget;
    unsigned long veneer_cursor;
    size_t code_size;
    void *entry;
    void *code;
    void *clone;
    int ret;

    if (!patch_size || patch_size != hook->patch_size || patch_size > KSU_INLINE_MAX_PATCH_SIZE)
        return -EINVAL;

    ret = ksu_x86_check_target(hook);
    if (ret)
        return ret;

    veneer_budget = hook->patch_size * KSU_X86_64_VENEER_SIZE;
    code_size = PAGE_ALIGN(((unsigned long)hook->target & ~PAGE_MASK) + hook->patch_size +
                           KSU_X86_64_DIRECT_BRANCH_SIZE + veneer_budget + KSU_X86_64_ENTRY_SIZE + 15);
    code = ksu_inline_hook_code_alloc(code_size);
    if (!code)
        return -ENOMEM;

    hook->keep_storage = true;
    hook->code = code;
    hook->code_size = code_size;

    clone = (u8 *)code + ((unsigned long)hook->target & ~PAGE_MASK);
    hook->clone = clone;

    ret = ksu_x86_build_reinsn(hook, (unsigned long)clone, &veneer_cursor);
    if (ret)
        goto err_free;

    entry = PTR_ALIGN((void *)veneer_cursor, 16);
    ret = ksu_x86_make_entry_stub(hook, entry);
    if (ret)
        goto err_free;
    hook->trampoline = entry;

    ret = ksu_x86_make_target_patch(hook, entry, patch);
    if (ret)
        goto err_free;

    pr_info("inline_hook: prepared x86 target=%px mode=reinsn patch=%zu trampoline=%px clone=%px\n", hook->target,
            hook->patch_size, entry, hook->clone);
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

#endif /* __x86_64__ */
