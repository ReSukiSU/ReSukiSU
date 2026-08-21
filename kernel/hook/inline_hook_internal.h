#ifndef __KSU_INLINE_HOOK_INTERNAL_H
#define __KSU_INLINE_HOOK_INTERNAL_H

#include "hook/inline_hook.h"

#include <linux/types.h>

#define KSU_INLINE_INVALID_SLOT (-1)

void *ksu_inline_hook_arch_normalize_target(void *target);
size_t ksu_inline_hook_arch_patch_size(void *target);
int ksu_inline_hook_arch_make_branch(void *to, u8 *patch, size_t patch_size);
int ksu_inline_hook_arch_prepare(struct ksu_inline_hook *hook, u8 *patch, size_t patch_size);
void ksu_inline_hook_arch_release(struct ksu_inline_hook *hook);
int ksu_inline_hook_set_fallback(struct ksu_inline_hook *hook);
void ksu_inline_hook_clear_fallback(struct ksu_inline_hook *hook);

#endif
