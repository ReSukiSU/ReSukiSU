/* SPDX-License-Identifier: GPL-2.0-only */

#ifdef __x86_64__

#include <linux/linkage.h>

SYM_FUNC_START(ksu_inline_hook_x86_64_entry_with_after)
    pushq %rbp
    movq %rsp, %rbp
    subq $64, %rsp

    movq %rdi, 0(%rsp)
    movq %rsi, 8(%rsp)
    movq %rdx, 16(%rsp)
    movq %rcx, 24(%rsp)
    movq %r8, 32(%rsp)
    movq %r9, 40(%rsp)
    movq %r11, 48(%rsp)
    leaq 8(%rbp), %rax
    movq %rax, 56(%rsp)

    call *%r10

    movq 48(%rsp), %rdi
    movq %rax, %rsi
    movq %rsp, %rdx
    call ksu_inline_hook_after

    leave
    ret
SYM_FUNC_END(ksu_inline_hook_x86_64_entry_with_after)

SYM_FUNC_START(ksu_inline_hook_x86_64_entry_with_before)
    pushq %rbp
    movq %rsp, %rbp
    subq $64, %rsp

    movq %rdi, 0(%rsp)
    movq %rsi, 8(%rsp)
    movq %rdx, 16(%rsp)
    movq %rcx, 24(%rsp)
    movq %r8, 32(%rsp)
    movq %r9, 40(%rsp)
    leaq 8(%rbp), %rax
    movq %rax, 56(%rsp)

    movq %r11, %rdi
    movq %rsp, %rsi
    call ksu_inline_hook_before

    movq 0(%rsp), %rdi
    movq 8(%rsp), %rsi
    movq 16(%rsp), %rdx
    movq 24(%rsp), %rcx
    movq 32(%rsp), %r8
    movq 40(%rsp), %r9
    call *%rax

    leave
    ret
SYM_FUNC_END(ksu_inline_hook_x86_64_entry_with_before)

SYM_FUNC_START(ksu_inline_hook_x86_64_entry_with_before_and_after)
    pushq %rbp
    movq %rsp, %rbp
    subq $64, %rsp

    movq %rdi, 0(%rsp)
    movq %rsi, 8(%rsp)
    movq %rdx, 16(%rsp)
    movq %rcx, 24(%rsp)
    movq %r8, 32(%rsp)
    movq %r9, 40(%rsp)
    movq %r11, 48(%rsp)
    leaq 8(%rbp), %rax
    movq %rax, 56(%rsp)

    movq %r11, %rdi
    movq %rsp, %rsi
    call ksu_inline_hook_before

    movq 0(%rsp), %rdi
    movq 8(%rsp), %rsi
    movq 16(%rsp), %rdx
    movq 24(%rsp), %rcx
    movq 32(%rsp), %r8
    movq 40(%rsp), %r9
    call *%rax

    movq 48(%rsp), %rdi
    movq %rax, %rsi
    movq %rsp, %rdx
    call ksu_inline_hook_after

    leave
    ret
SYM_FUNC_END(ksu_inline_hook_x86_64_entry_with_before_and_after)

#endif
