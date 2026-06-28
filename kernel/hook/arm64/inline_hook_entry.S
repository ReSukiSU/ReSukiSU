/* SPDX-License-Identifier: GPL-2.0-only */

#ifdef __aarch64__

#include <linux/linkage.h>

#ifndef SYM_FUNC_START
#define SYM_FUNC_START(name) \
    ENTRY(name)
#endif

#ifndef SYM_FUNC_END
#define SYM_FUNC_END(name) \
    END(name)
#endif

SYM_FUNC_START(ksu_inline_hook_arm64_entry_with_after)
#ifdef CONFIG_ARM64_BTI_KERNEL
    .inst 0xd50324df
#endif
    stp x29, x30, [sp, #-128]!
    mov x29, sp

    str x16, [sp, #16]
    stp x0, x1, [sp, #32]
    stp x2, x3, [sp, #48]
    stp x4, x5, [sp, #64]
    stp x6, x7, [sp, #80]
    str x8, [sp, #96]

    ldp x0, x1, [sp, #32]
    ldp x2, x3, [sp, #48]
    ldp x4, x5, [sp, #64]
    ldp x6, x7, [sp, #80]
    ldr x8, [sp, #96]
    blr x17

    str x0, [sp, #24]
    ldr x0, [sp, #16]
    ldr x1, [sp, #24]
    add x2, sp, #32
    bl ksu_inline_hook_after

    ldp x29, x30, [sp], #128
    ret
SYM_FUNC_END(ksu_inline_hook_arm64_entry_with_after)

SYM_FUNC_START(ksu_inline_hook_arm64_entry_with_before)
#ifdef CONFIG_ARM64_BTI_KERNEL
    .inst 0xd50324df
#endif
    stp x29, x30, [sp, #-128]!
    mov x29, sp

    str x16, [sp, #16]
    stp x0, x1, [sp, #32]
    stp x2, x3, [sp, #48]
    stp x4, x5, [sp, #64]
    stp x6, x7, [sp, #80]
    str x8, [sp, #96]

    mov x0, x16
    add x1, sp, #32
    bl ksu_inline_hook_before
    mov x9, x0

    ldp x0, x1, [sp, #32]
    ldp x2, x3, [sp, #48]
    ldp x4, x5, [sp, #64]
    ldp x6, x7, [sp, #80]
    ldr x8, [sp, #96]
    blr x9

    ldp x29, x30, [sp], #128
    ret
SYM_FUNC_END(ksu_inline_hook_arm64_entry_with_before)

SYM_FUNC_START(ksu_inline_hook_arm64_entry_with_before_and_after)
#ifdef CONFIG_ARM64_BTI_KERNEL
    .inst 0xd50324df
#endif
    stp x29, x30, [sp, #-128]!
    mov x29, sp

    str x16, [sp, #16]
    stp x0, x1, [sp, #32]
    stp x2, x3, [sp, #48]
    stp x4, x5, [sp, #64]
    stp x6, x7, [sp, #80]
    str x8, [sp, #96]

    mov x0, x16
    add x1, sp, #32
    bl ksu_inline_hook_before
    mov x9, x0

    ldp x0, x1, [sp, #32]
    ldp x2, x3, [sp, #48]
    ldp x4, x5, [sp, #64]
    ldp x6, x7, [sp, #80]
    ldr x8, [sp, #96]
    blr x9

    str x0, [sp, #24]
    ldr x0, [sp, #16]
    ldr x1, [sp, #24]
    add x2, sp, #32
    bl ksu_inline_hook_after

    ldp x29, x30, [sp], #128
    ret
SYM_FUNC_END(ksu_inline_hook_arm64_entry_with_before_and_after)

#endif
