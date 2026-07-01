#ifndef __KSU_H_ARCH
#define __KSU_H_ARCH

#include <linux/version.h>

#if defined(__aarch64__)

#define __PT_PARM1_REG regs[0]
#define __PT_PARM2_REG regs[1]
#define __PT_PARM3_REG regs[2]
#define __PT_SYSCALL_PARM4_REG regs[3]
#define __PT_CCALL_PARM4_REG regs[3]
#define __PT_PARM5_REG regs[4]
#define __PT_PARM6_REG regs[5]
#define __PT_PARM7(x) (__PT_REGS_CAST(x)->regs[6])
#define __PT_RET_REG regs[30]
#define __PT_FP_REG regs[29] /* Works only with CONFIG_FRAME_POINTER */
#define __PT_RC_REG regs[0]
#define __PT_SP_REG sp
#define __PT_IP_REG pc
#define __PT_ORIG_SYSCALL_REG regs[8]

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0)
#define SYS_REBOOT_SYMBOL "__arm64_sys_reboot"
#define SYS_NEWFSTATAT_SYMBOL "__arm64_sys_newfstatat"
#define SYS_FACCESSAT_SYMBOL "__arm64_sys_faccessat"
#define SYS_EXECVE_SYMBOL "__arm64_sys_execve"
#define SYS_SETNS_SYMBOL __arm64_sys_setns
// https://cs.android.com/android/kernel/superproject/+/common-android-mainline:common/scripts/syscalltbl.sh;l=57;drc=9142be9e6443fd641ca37f820efe00d9cd890eb1
// https://cs.android.com/android/kernel/superproject/+/common-android-mainline:common/scripts/syscall.tbl;l=104;drc=b36d4b6aa88ef039647228b98c59a875e92f8c8e
#define SYS_FSTAT_SYMBOL "__arm64_sys_newfstat"
#define SYS_FSTAT64_SYMBOL "__arm64_sys_fstat64"
#define SYS_FSTATAT64_SYMBOL "__arm64_sys_fstatat64"
#else
#define SYS_REBOOT_SYMBOL "sys_reboot"
#define SYS_NEWFSTATAT_SYMBOL "sys_newfstatat"
#define SYS_FACCESSAT_SYMBOL "sys_faccessat"
#define SYS_EXECVE_SYMBOL "sys_execve"
#define SYS_SETNS_SYMBOL sys_setns
#define SYS_FSTAT_SYMBOL "sys_newfstat"
#define SYS_FSTAT64_SYMBOL "sys_fstat64"
#define SYS_FSTATAT64_SYMBOL "sys_fstatat64"
#endif

#elif defined(__x86_64__)

#define __PT_PARM1_REG di
#define __PT_PARM2_REG si
#define __PT_PARM3_REG dx
/* syscall uses r10 for PARM4 */
#define __PT_SYSCALL_PARM4_REG r10
#define __PT_CCALL_PARM4_REG cx
#define __PT_PARM5_REG r8
#define __PT_PARM6_REG r9
#define __PT_PARM7(x) (*(unsigned long *)(__PT_REGS_CAST(x)->sp + sizeof(unsigned long)))
#define __PT_RET_REG sp
#define __PT_FP_REG bp
#define __PT_RC_REG ax
#define __PT_SP_REG sp
#define __PT_IP_REG ip
#define __PT_ORIG_SYSCALL_REG orig_ax

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0)
#define SYS_REBOOT_SYMBOL "__x64_sys_reboot"
#define SYS_NEWFSTATAT_SYMBOL "__x64_sys_newfstatat"
#define SYS_FACCESSAT_SYMBOL "__x64_sys_faccessat"
#define SYS_EXECVE_SYMBOL "__x64_sys_execve"
#define SYS_SETNS_SYMBOL __x64_sys_setns
#define SYS_FSTAT_SYMBOL "__x64_sys_newfstat"
#define SYS_FSTAT64_SYMBOL "__x64_sys_fstat64"
#define SYS_FSTATAT64_SYMBOL "__x64_sys_fstatat64"
#else
#define SYS_REBOOT_SYMBOL "sys_reboot"
#define SYS_NEWFSTATAT_SYMBOL "sys_newfstatat"
#define SYS_FACCESSAT_SYMBOL "sys_faccessat"
#define SYS_EXECVE_SYMBOL "sys_execve"
#define SYS_SETNS_SYMBOL sys_setns
#define SYS_FSTAT_SYMBOL "sys_newfstat"
#define SYS_FSTAT64_SYMBOL "sys_fstat64"
#define SYS_FSTATAT64_SYMBOL "sys_fstatat64"
#endif

#elif defined(__arm__)

#define __PT_PARM1_REG ARM_r0
#define __PT_PARM2_REG ARM_r1
#define __PT_PARM3_REG ARM_r2
#define __PT_SYSCALL_PARM4_REG ARM_r3
#define __PT_CCALL_PARM4_REG ARM_r3
#define __PT_PARM5(x) (*(unsigned long *)(__PT_REGS_CAST(x)->ARM_sp))
#define __PT_PARM6(x) (*(unsigned long *)(__PT_REGS_CAST(x)->ARM_sp + sizeof(unsigned long)))
#define __PT_PARM7(x) (*(unsigned long *)(__PT_REGS_CAST(x)->ARM_sp + 2 * sizeof(unsigned long)))
#define __PT_RET_REG ARM_lr
#define __PT_FP_REG ARM_fp
#define __PT_RC_REG ARM_r0
#define __PT_SP_REG ARM_sp
#define __PT_IP_REG ARM_pc
#define __PT_ORIG_SYSCALL_REG ARM_ORIG_r0

#define SYS_REBOOT_SYMBOL "sys_reboot"
#define SYS_EXECVE_SYMBOL "sys_execve"
#define SYS_SETNS_SYMBOL sys_setns
#define SYS_FSTAT_SYMBOL "sys_newfstat"
#define SYS_FSTATAT64_SYMBOL "sys_fstatat64"
#define SYS_FACCESSAT_SYMBOL "sys_faccessat"
#define SYS_NEWFSTATAT_SYMBOL "sys_newfstatat"
#define SYS_FSTAT64_SYMBOL "sys_fstat64"

#else
#error "Unsupported arch"
#endif

/* allow some architecutres to override `struct pt_regs` */
#ifndef __PT_REGS_CAST
#define __PT_REGS_CAST(x) (x)
#endif

#define PT_REGS_PARM1(x) (__PT_REGS_CAST(x)->__PT_PARM1_REG)
#define PT_REGS_PARM2(x) (__PT_REGS_CAST(x)->__PT_PARM2_REG)
#define PT_REGS_PARM3(x) (__PT_REGS_CAST(x)->__PT_PARM3_REG)
#define PT_REGS_SYSCALL_PARM4(x) (__PT_REGS_CAST(x)->__PT_SYSCALL_PARM4_REG)
#define PT_REGS_CCALL_PARM4(x) (__PT_REGS_CAST(x)->__PT_CCALL_PARM4_REG)
#ifndef __PT_PARM5
#define PT_REGS_PARM5(x) (__PT_REGS_CAST(x)->__PT_PARM5_REG)
#else
#define PT_REGS_PARM5(x) __PT_PARM5(x)
#endif
#ifndef __PT_PARM6
#define PT_REGS_PARM6(x) (__PT_REGS_CAST(x)->__PT_PARM6_REG)
#else
#define PT_REGS_PARM6(x) __PT_PARM6(x)
#endif
#define PT_REGS_PARM7(x) __PT_PARM7(x)
#define PT_REGS_RET(x) (__PT_REGS_CAST(x)->__PT_RET_REG)
#define PT_REGS_FP(x) (__PT_REGS_CAST(x)->__PT_FP_REG)
#define PT_REGS_RC(x) (__PT_REGS_CAST(x)->__PT_RC_REG)
#define PT_REGS_SP(x) (__PT_REGS_CAST(x)->__PT_SP_REG)
#define PT_REGS_IP(x) (__PT_REGS_CAST(x)->__PT_IP_REG)
#define PT_REGS_ORIG_SYSCALL(x) (__PT_REGS_CAST(x)->__PT_ORIG_SYSCALL_REG)

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0)
#define PT_REAL_REGS(regs) ((struct pt_regs *)PT_REGS_PARM1(regs))
#else
#define PT_REAL_REGS(regs) ((regs))
#endif

#endif
