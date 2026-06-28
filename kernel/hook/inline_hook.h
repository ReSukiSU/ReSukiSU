/* SPDX-License-Identifier: GPL-2.0-only */

#ifndef __KSU_INLINE_HOOK_H
#define __KSU_INLINE_HOOK_H

#include <linux/types.h>
#include <asm/ptrace.h>

struct ksu_inline_hook;

typedef void (*ksu_inline_hook_callback_t)(struct pt_regs *regs);

#if defined(__aarch64__) && defined(CONFIG_CFI_CLANG)
#define KSU_INLINE_HOOK_TARGET(fn)                                                                                     \
    ({                                                                                                                 \
        void *__addr;                                                                                                  \
        asm("adrp %0, " #fn "\n\t"                                                                                     \
            "add  %0, %0, :lo12:" #fn                                                                                  \
            : "=r"(__addr));                                                                                           \
        __addr;                                                                                                        \
    })
#else
#define KSU_INLINE_HOOK_TARGET(fn) ((void *)(fn))
#endif

struct ksu_inline_hook_config {
    void *target;
    ksu_inline_hook_callback_t before;
    ksu_inline_hook_callback_t after;
};

struct ksu_inline_hook *ksu_inline_hook_register(const struct ksu_inline_hook_config config);
void ksu_inline_hook_unregister(struct ksu_inline_hook *hook);

#endif
