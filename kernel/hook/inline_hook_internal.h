#ifndef __KSU_INLINE_HOOK_INTERNAL_H
#define __KSU_INLINE_HOOK_INTERNAL_H

#include "hook/inline_hook.h"

#include <linux/types.h>

#define KSU_INLINE_MAX_PATCH_SIZE 32
#define KSU_INLINE_INVALID_SLOT (-1)

struct ksu_inline_hook {
    void *target;
    ksu_inline_hook_callback_t before;
    ksu_inline_hook_callback_t after;
    u8 orig[KSU_INLINE_MAX_PATCH_SIZE];
    size_t patch_size;
    void *trampoline;
    void *clone;
    void *code;
    size_t code_size;
    int slot;
    bool unregistering;
    bool keep_storage;
    bool active;
};

void *ksu_inline_hook_arch_normalize_target(void *target);
size_t ksu_inline_hook_arch_patch_size(void);
int ksu_inline_hook_arch_make_branch(void *to, u8 *patch, size_t patch_size);
int ksu_inline_hook_arch_prepare(struct ksu_inline_hook *hook, u8 *patch, size_t patch_size);
void ksu_inline_hook_arch_release(struct ksu_inline_hook *hook);
void ksu_inline_hook_arch_setup_regs(struct pt_regs *regs, unsigned long *arg_regs);
void ksu_inline_hook_arch_update_args(const struct pt_regs *regs, unsigned long *arg_regs);
void ksu_inline_hook_arch_set_ret(struct pt_regs *regs, unsigned long ret);
unsigned long ksu_inline_hook_arch_get_ret(const struct pt_regs *regs);
void *ksu_inline_hook_before(struct ksu_inline_hook *hook, unsigned long *arg_regs);
unsigned long ksu_inline_hook_after(struct ksu_inline_hook *hook, unsigned long ret, unsigned long *arg_regs);
unsigned long ksu_inline_hook_entry_dispatch(struct ksu_inline_hook *hook, unsigned long arg0, unsigned long arg1,
                                             unsigned long arg2, unsigned long arg3, unsigned long arg4,
                                             unsigned long arg5, unsigned long arg6);
int ksu_inline_hook_set_fallback(struct ksu_inline_hook *hook);
void ksu_inline_hook_clear_fallback(struct ksu_inline_hook *hook);

#endif
