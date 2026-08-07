/* SPDX-License-Identifier: GPL-2.0-only */

#ifdef __arm__

#include <linux/linkage.h>

#ifndef SYM_FUNC_START
#define SYM_FUNC_START(name) ENTRY(name)
#endif

#ifndef SYM_FUNC_END
#define SYM_FUNC_END(name) END(name)
#endif

    .syntax unified
#ifdef CONFIG_THUMB2_KERNEL
    .thumb
#else
    .arm
#endif

.macro ksu_arm32_func_start name
#ifdef CONFIG_THUMB2_KERNEL
    .thumb_func
#endif
    SYM_FUNC_START(\name)
.endm

/*
 * The per-hook stub saves the caller's LR before entering here.
 *
 * Stack layout after the 52-byte allocation:
 *
 *   0..11   copied r4-r6 stack arguments for the clone
 *   16..28  r0-r3
 *   32      stack pointer at the original function entry
 *   36      struct ksu_inline_hook *
 *   40      clone address
 *   52      caller LR saved by the per-hook stub
 */
.macro ksu_arm32_save_context
    sub sp, sp, #52
    str lr, [sp, #40]
    add lr, sp, #16
    stmia lr, {r0-r3}
    add r0, sp, #56
    str r0, [sp, #32]
    str ip, [sp, #36]
.endm

.macro ksu_arm32_copy_stack_args
    ldr ip, [sp, #32]
    ldr r0, [ip, #0]
    str r0, [sp, #0]
    ldr r0, [ip, #4]
    str r0, [sp, #4]
    ldr r0, [ip, #8]
    str r0, [sp, #8]
.endm

.macro ksu_arm32_restore_args
    add ip, sp, #16
    ldmia ip, {r0-r3}
.endm

.macro ksu_arm32_return
    ldr lr, [sp, #52]
    add sp, sp, #56
    bx lr
.endm

ksu_arm32_func_start ksu_inline_hook_arm32_entry_with_after
    ksu_arm32_save_context
    ksu_arm32_copy_stack_args
    ksu_arm32_restore_args
    ldr ip, [sp, #40]
    blx ip

    mov r1, r0
    ldr r0, [sp, #36]
    add r2, sp, #16
    bl ksu_inline_hook_after

    ksu_arm32_return
SYM_FUNC_END(ksu_inline_hook_arm32_entry_with_after)

ksu_arm32_func_start ksu_inline_hook_arm32_entry_with_before
    ksu_arm32_save_context

    mov r0, ip
    add r1, sp, #16
    bl ksu_inline_hook_before
    str r0, [sp, #40]

    ksu_arm32_copy_stack_args
    ksu_arm32_restore_args
    ldr ip, [sp, #40]
    blx ip

    ksu_arm32_return
SYM_FUNC_END(ksu_inline_hook_arm32_entry_with_before)

ksu_arm32_func_start ksu_inline_hook_arm32_entry_with_before_and_after
    ksu_arm32_save_context

    mov r0, ip
    add r1, sp, #16
    bl ksu_inline_hook_before
    str r0, [sp, #40]

    ksu_arm32_copy_stack_args
    ksu_arm32_restore_args
    ldr ip, [sp, #40]
    blx ip

    mov r1, r0
    ldr r0, [sp, #36]
    add r2, sp, #16
    bl ksu_inline_hook_after

    ksu_arm32_return
SYM_FUNC_END(ksu_inline_hook_arm32_entry_with_before_and_after)

#endif /* __arm__ */
