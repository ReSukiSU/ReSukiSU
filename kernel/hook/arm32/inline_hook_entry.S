/* SPDX-License-Identifier: GPL-2.0-only */

#ifdef __arm__

#include <linux/linkage.h>

#ifndef SYM_FUNC_START
#define SYM_FUNC_START(name) \
    ENTRY(name)
#endif

#ifndef SYM_FUNC_END
#define SYM_FUNC_END(name) \
    END(name)
#endif

    .arm
SYM_FUNC_START(ksu_inline_hook_arm32_entry_with_after)
    sub sp, sp, #24
    stmia sp, {r0-r3}
    add r0, sp, #28
    str r0, [sp, #16]
    str ip, [sp, #20]

    blx lr

    mov r1, r0
    ldr r0, [sp, #20]
    mov r2, sp
    bl ksu_inline_hook_after

    ldr lr, [sp, #24]
    add sp, sp, #28
    bx lr
SYM_FUNC_END(ksu_inline_hook_arm32_entry_with_after)

SYM_FUNC_START(ksu_inline_hook_arm32_entry_with_before)
    sub sp, sp, #28
    stmia sp, {r0-r3}
    add r0, sp, #28
    str r0, [sp, #16]
    str ip, [sp, #20]
    str lr, [sp, #24]

    mov r0, ip
    mov r1, sp
    bl ksu_inline_hook_before
    mov ip, r0

    ldmia sp, {r0-r3}
    blx ip

    ldr lr, [sp, #24]
    add sp, sp, #28
    bx lr
SYM_FUNC_END(ksu_inline_hook_arm32_entry_with_before)

SYM_FUNC_START(ksu_inline_hook_arm32_entry_with_before_and_after)
    sub sp, sp, #28
    stmia sp, {r0-r3}
    add r0, sp, #28
    str r0, [sp, #16]
    str ip, [sp, #20]
    str lr, [sp, #24]

    mov r0, ip
    mov r1, sp
    bl ksu_inline_hook_before
    mov ip, r0

    ldmia sp, {r0-r3}
    blx ip

    mov r1, r0
    ldr r0, [sp, #20]
    mov r2, sp
    bl ksu_inline_hook_after

    ldr lr, [sp, #24]
    add sp, sp, #28
    bx lr
SYM_FUNC_END(ksu_inline_hook_arm32_entry_with_before_and_after)

#endif
