# Arm32 Compat Syscall Rebase and ABI Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebase the `32bit-on-64bit-kernel-lkm` work onto `main` and make arm32 compat syscall dispatch and argument handling use the arm32 ABI correctly.

**Architecture:** Keep separate native and compat syscall dispatch tables on arm64. Native dispatch continues to store the original syscall number in the native arm64 register slot; compat dispatch stores and reads the original syscall number from arm32 `r7` (`pt_regs.regs[7]`). Compat user pointers are converted with `compat_ptr` before kernel code reads them, while native paths retain the existing accessors.

**Tech Stack:** Linux kernel C, Kbuild, Git rebase, shell syntax checks.

**Spec:** User request in the conversation: rebase the linked branch onto `main`, fix its problems, and correct arm32 ABI reads that incorrectly use arm64 ABI handling.

## Global Constraints

- Preserve current `main` behavior and recent fixes while integrating compat support.
- Keep compat-only code behind `CONFIG_COMPAT` and arm64 conditionals.
- Verify no conflict markers or unresolved index entries remain.

---

### Task 1: Finish the rebase merge and preserve build metadata

**Files:**
- Modify: `.gitignore`
- Modify: `kernel/Kbuild`
- Modify: `kernel/build-all.sh`
- Modify: `kernel/tools/kernel_compat.mk`

- [ ] Combine the branch additions with the current `main` entries without dropping `main` targets or compatibility checks.
- [ ] Run `git diff --check` and inspect `git status` for unresolved entries.

### Task 2: Correct compat dispatcher ABI and lifecycle

**Files:**
- Modify: `kernel/hook/arm64/syscall_hook.c`
- Modify: `kernel/hook/syscall_hook_manager.c`
- Modify: `kernel/hook/syscall_hook.h`

- [ ] Store/read compat original syscall numbers in `regs->regs[7]`, matching arm32 `r7`; keep native `regs[8]` handling unchanged.
- [ ] Fix compat no-syscall slot discovery to inspect the compat table and guard compat initialization with `CONFIG_COMPAT`.
- [ ] Keep compat table restoration and hook registration bounded by the compat syscall count.

### Task 3: Use 32-bit user pointer representation in hook handlers

**Files:**
- Modify: `kernel/hook/syscall_event_bridge.c`
- Modify: `kernel/feature/sucompat.c`
- Modify: `kernel/runtime/ksud_integration.c`
- Modify: `kernel/include/arch.h`

- [ ] Add arm64 compat helpers that read syscall arguments as 32-bit values and convert user pointers with `compat_ptr`.
- [ ] Use those helpers for compat execve, faccessat, fstatat, read, and fstat paths; keep native paths unchanged.
- [ ] Restore compat argv representation through `struct user_arg_ptr` so sulog and ksud parsing read a 32-bit pointer array.
- [ ] Fix the misspelled sucompat declaration and preserve the rebase-side execve/execveat behavior.

### Task 4: Verify the rebased branch

**Files:**
- No additional source files.

- [ ] Run `git diff --check` and repository static searches for conflict markers and arm32 dispatch accesses.
- [ ] Run available kernel/build script syntax checks and targeted compilation checks; report unavailable full-kernel builds explicitly.
- [ ] Review the final diff against `main` and confirm the branch is clean apart from intentional fix commits.
