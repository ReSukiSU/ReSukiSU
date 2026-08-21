/* SPDX-License-Identifier: GPL-2.0-only */

#ifndef __KSU_INLINE_HOOK_H
#define __KSU_INLINE_HOOK_H

#include <linux/types.h>
#include <asm/ptrace.h>

#define KSU_INLINE_MAX_PATCH_SIZE 32

enum ksu_inline_hook_abi {
    KSU_INLINE_HOOK_ABI_NATIVE = 0,
    KSU_INLINE_HOOK_ABI_ARM64_SYSCALL,
};

struct ksu_inline_hook {
    void *target;
    void *dispatcher;
    enum ksu_inline_hook_abi abi;
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
    void *dispatcher;
    enum ksu_inline_hook_abi abi;
    /* Published after clone preparation and before the target becomes reachable. */
    struct ksu_inline_hook **owner;
};

struct ksu_inline_hook *ksu_inline_hook_register(const struct ksu_inline_hook_config config);
void ksu_inline_hook_unregister(struct ksu_inline_hook *hook);

#endif
