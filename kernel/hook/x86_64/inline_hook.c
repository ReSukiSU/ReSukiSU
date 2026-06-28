/* SPDX-License-Identifier: GPL-2.0-only */

#ifdef __x86_64__

#include "hook/inline_hook_internal.h"

#include <linux/errno.h>
#include <linux/gfp.h>
#include <linux/kasan.h>
#include <linux/mm.h>
#include <linux/mutex.h>
#include <linux/numa.h>
#include <linux/random.h>
#include <linux/string.h>
#include <linux/vmalloc.h>
#include <linux/version.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 10, 0)
#include <linux/execmem.h>
#endif

#include <asm/pgtable.h>
#include <asm/setup.h>

#include "hook/patch_memory.h"

#define KSU_X86_64_PATCH_SIZE 12
#define KSU_X86_64_ENTRY_SIZE 40
#ifdef CONFIG_KASAN
#define KSU_X86_64_MODULE_ALIGN (PAGE_SIZE << KASAN_SHADOW_SCALE_SHIFT)
#else
#define KSU_X86_64_MODULE_ALIGN PAGE_SIZE
#endif

extern void ksu_inline_hook_x86_64_entry_with_after(void);
extern void ksu_inline_hook_x86_64_entry_with_before(void);
extern void ksu_inline_hook_x86_64_entry_with_before_and_after(void);

void *ksu_inline_hook_arch_normalize_target(void *target)
{
    return target;
}

size_t ksu_inline_hook_arch_patch_size(void)
{
    return KSU_X86_64_PATCH_SIZE;
}

int ksu_inline_hook_arch_make_branch(void *to, u8 *patch, size_t patch_size)
{
    if (patch_size != KSU_X86_64_PATCH_SIZE)
        return -EINVAL;

    patch[0] = 0x48;
    patch[1] = 0xb8;
    memcpy(patch + 2, &to, sizeof(to));
    patch[10] = 0xff;
    patch[11] = 0xe0;

    return 0;
}

unsigned long ksu_inline_hook_arch_get_ret(const struct pt_regs *regs)
{
    return regs->ax;
}

void ksu_inline_hook_arch_setup_regs(struct pt_regs *regs, unsigned long *arg_regs)
{
    regs->di = arg_regs[0];
    regs->si = arg_regs[1];
    regs->dx = arg_regs[2];
    regs->cx = arg_regs[3];
    regs->r8 = arg_regs[4];
    regs->r9 = arg_regs[5];
    regs->sp = arg_regs[6];
}

void ksu_inline_hook_arch_update_args(const struct pt_regs *regs, unsigned long *arg_regs)
{
    arg_regs[0] = regs->di;
    arg_regs[1] = regs->si;
    arg_regs[2] = regs->dx;
    arg_regs[3] = regs->cx;
    arg_regs[4] = regs->r8;
    arg_regs[5] = regs->r9;
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

static void *ksu_inline_hook_clone_code_alloc(size_t size)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 10, 0)
    void *p;

    if (PAGE_ALIGN(size) > MODULES_LEN)
        return NULL;

    p = __vmalloc_node_range(size, KSU_X86_64_MODULE_ALIGN, MODULES_VADDR + ksu_inline_get_module_load_offset(),
                             MODULES_END, GFP_KERNEL, PAGE_KERNEL, 0, NUMA_NO_NODE, __builtin_return_address(0));
    if (p && kasan_module_alloc(p, size) < 0) {
        vfree(p);
        return NULL;
    }

    return p;
#else
    return execmem_alloc(EXECMEM_DEFAULT, size);
#endif
}

static void ksu_inline_make_entry_stub(struct ksu_inline_hook *hook, void *buf)
{
    u8 *code = buf;
    void *clone = hook->clone;
    void *entry;

    if (hook->before && hook->after)
        entry = ksu_inline_hook_x86_64_entry_with_before_and_after;
    else if (hook->before)
        entry = ksu_inline_hook_x86_64_entry_with_before;
    else
        entry = ksu_inline_hook_x86_64_entry_with_after;

    code[0] = 0x49;
    code[1] = 0xbb;
    memcpy(code + 2, &hook, sizeof(hook));
    code[10] = 0x49;
    code[11] = 0xba;
    memcpy(code + 12, &clone, sizeof(clone));
    code[20] = 0x48;
    code[21] = 0xb8;
    memcpy(code + 22, &entry, sizeof(entry));
    code[30] = 0xff;
    code[31] = 0xe0;
    memset(code + 32, 0x90, KSU_X86_64_ENTRY_SIZE - 32);
}

int ksu_inline_hook_arch_prepare(struct ksu_inline_hook *hook, u8 *patch, size_t patch_size)
{
    void *code;
    void *entry;
    u8 *clone;
    int ret;

    if (patch_size != KSU_X86_64_PATCH_SIZE)
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
