/* SPDX-License-Identifier: GPL-2.0-only */

#ifdef __x86_64__

#include <linux/linkage.h>

#ifdef CONFIG_RETPOLINE
#include <asm/nospec-branch.h>
#endif

#ifndef SYM_FUNC_START
#define SYM_FUNC_START(name) ENTRY(name)
#endif

#ifndef SYM_FUNC_END
#define SYM_FUNC_END(name) END(name)
#endif

.macro ksu_x86_64_endbr
#ifdef CONFIG_X86_KERNEL_IBT
    .byte 0xf3, 0x0f, 0x1e, 0xfa
#endif
.endm

.macro ksu_x86_64_call reg
#ifdef CONFIG_RETPOLINE
    CALL_NOSPEC \reg
#else
    call *%\reg
#endif
.endm

/*
 * Stack layout after the 96-byte allocation:
 *
 *   0..31   copied stack arguments for the clone
 *   32..72  rdi, rsi, rdx, rcx, r8, r9
 *   80      stack pointer at the original function entry
 *   88      struct ksu_inline_hook *
 */
.macro ksu_x86_64_save_context
    pushq %rbp
    movq %rsp, %rbp
    subq $96, %rsp

    movq %rdi, 32(%rsp)
    movq %rsi, 40(%rsp)
    movq %rdx, 48(%rsp)
    movq %rcx, 56(%rsp)
    movq %r8, 64(%rsp)
    movq %r9, 72(%rsp)
    leaq 8(%rbp), %rax
    movq %rax, 80(%rsp)
    movq %r11, 88(%rsp)
.endm

.macro ksu_x86_64_copy_stack_args
    movq 80(%rsp), %r11
    movq 8(%r11), %rax
    movq %rax, 0(%rsp)
    movq 16(%r11), %rax
    movq %rax, 8(%rsp)
    movq 24(%r11), %rax
    movq %rax, 16(%rsp)
    movq 32(%r11), %rax
    movq %rax, 24(%rsp)
.endm

.macro ksu_x86_64_restore_args
    movq 32(%rsp), %rdi
    movq 40(%rsp), %rsi
    movq 48(%rsp), %rdx
    movq 56(%rsp), %rcx
    movq 64(%rsp), %r8
    movq 72(%rsp), %r9
.endm

SYM_FUNC_START(ksu_inline_hook_x86_64_entry_with_after)
    ksu_x86_64_endbr
    ksu_x86_64_save_context
    ksu_x86_64_copy_stack_args
    ksu_x86_64_restore_args
    ksu_x86_64_call r10

    movq 88(%rsp), %rdi
    movq %rax, %rsi
    leaq 32(%rsp), %rdx
    call ksu_inline_hook_after

    leave
    ret
SYM_FUNC_END(ksu_inline_hook_x86_64_entry_with_after)

SYM_FUNC_START(ksu_inline_hook_x86_64_entry_with_before)
    ksu_x86_64_endbr
    ksu_x86_64_save_context

    movq 88(%rsp), %rdi
    leaq 32(%rsp), %rsi
    call ksu_inline_hook_before
    movq %rax, %r10

    ksu_x86_64_copy_stack_args
    ksu_x86_64_restore_args
    ksu_x86_64_call r10

    leave
    ret
SYM_FUNC_END(ksu_inline_hook_x86_64_entry_with_before)

SYM_FUNC_START(ksu_inline_hook_x86_64_entry_with_before_and_after)
    ksu_x86_64_endbr
    ksu_x86_64_save_context

    movq 88(%rsp), %rdi
    leaq 32(%rsp), %rsi
    call ksu_inline_hook_before
    movq %rax, %r10

    ksu_x86_64_copy_stack_args
    ksu_x86_64_restore_args
    ksu_x86_64_call r10

    movq 88(%rsp), %rdi
    movq %rax, %rsi
    leaq 32(%rsp), %rdx
    call ksu_inline_hook_after

    leave
    ret
SYM_FUNC_END(ksu_inline_hook_x86_64_entry_with_before_and_after)

#endif /* __x86_64__ */
