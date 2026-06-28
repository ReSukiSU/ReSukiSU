/* SPDX-License-Identifier: GPL-2.0-only */
/*
 * Copyright (C) 2023 bmax121. All Rights Reserved.
 */

#ifdef __aarch64__

#include "../patch_memory.h"
#include "klog.h" // IWYU pragma: keep
#include "linux/cpumask.h"
#include "linux/gfp.h" // IWYU pragma: keep
#include "linux/uaccess.h"
#include "linux/stop_machine.h"
#include "asm/cacheflush.h"

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 8, 0)
#include <asm-generic/fixmap.h>
#include <asm/fixmap.h>
#else
#include <linux/vmalloc.h>
#include <linux/mm.h>
#include <linux/highmem.h>
#endif

#include <linux/sched.h>

// https://github.com/torvalds/linux/blob/v5.4/arch/arm64/include/asm/pgtable.h#L61
#ifndef PTE_ADDR_LOW
#define PTE_ADDR_LOW (((_AT(pteval_t, 1) << (48 - PAGE_SHIFT)) - 1) << PAGE_SHIFT)
#endif

#ifndef PTE_ADDR_MASK
#define PTE_ADDR_MASK PTE_ADDR_LOW
#endif

#ifndef __pte_to_phys
#define __pte_to_phys(pte) (pte_val(pte) & PTE_ADDR_MASK)
#endif

#ifndef __pud_to_phys
#define __pud_to_phys(pud) __pte_to_phys(pud_pte(pud))
#endif

#ifndef __pmd_to_phys
#define __pmd_to_phys(pmd) __pte_to_phys(pmd_pte(pmd))
#endif

// https://github.com/torvalds/linux/commit/3b8c9f1cdfc506e94e992ae66b68bbe416f89610
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 20, 0)
__weak void __flush_icache_range(unsigned long start, unsigned long end)
{
    flush_icache_range(start, end);
}
#endif

// https://github.com/fuqiuluo/ovo/blob/f7da411458e87d32438dc14fce5a3313ed0c967e/ovo/mmuhack.c#L21

// Translate a kernel virtual address to a physical address by walking the
// init_mm page tables. Returns the physical address on success, or writes
// a non-zero error to *err. Callers must check *err before using the result,
// since physical address 0 is a valid address.
unsigned long phys_from_virt(unsigned long addr, int *err)
{
    struct mm_struct *mm = &init_mm;
    pgd_t *pgd;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
    p4d_t *p4d;
#endif
    pud_t *pud;
    pmd_t *pmd;
    pte_t *pte;

    *err = 0;

    pgd = pgd_offset(mm, addr);
    if (pgd_none(*pgd) || pgd_bad(*pgd))
        goto fail;
    pr_info("pgd of 0x%lx p=0x%lx v=0x%lx", addr, (uintptr_t)pgd, (uintptr_t)pgd_val(*pgd));

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
    p4d = p4d_offset(pgd, addr);
    if (p4d_none(*p4d) || p4d_bad(*p4d))
        goto fail;
    pr_info("p4d of 0x%lx p=0x%lx v=0x%lx", addr, (uintptr_t)p4d, (uintptr_t)p4d_val(*p4d));
#if defined(p4d_leaf)
    if (p4d_leaf(*p4d)) {
        pr_info("Address 0x%lx maps to a P4D-level huge page\n", addr);
        return __p4d_to_phys(*p4d) + ((addr & ~P4D_MASK));
    }
#endif
    pud = pud_offset(p4d, addr);
#else
    pud = pud_offset(pgd, addr);
#endif
    if (pud_none(*pud) || pud_bad(*pud))
        goto fail;
    pr_info("pud of 0x%lx p=0x%lx v=0x%lx", addr, (uintptr_t)pud, (uintptr_t)pud_val(*pud));
#if defined(pud_leaf)
    if (pud_leaf(*pud)) {
        pr_info("Address 0x%lx maps to a PUD-level huge page\n", addr);
        return __pud_to_phys(*pud) + ((addr & ~PUD_MASK));
    }
#elif defined(pud_sect)
    if (pud_sect(*pud)) {
        pr_info("Address 0x%lx maps to a PUD-level huge page\n", addr);
        return __pud_to_phys(*pud) + ((addr & ~PUD_MASK));
    }
#endif

    pmd = pmd_offset(pud, addr);
    pr_info("pmd of 0x%lx p=0x%lx v=0x%lx", addr, (uintptr_t)pmd, (uintptr_t)pmd_val(*pmd));
#if defined(pmd_leaf)
    if (pmd_leaf(*pmd)) {
        pr_info("Address 0x%lx maps to a PMD-level huge page\n", addr);
        return __pmd_to_phys(*pmd) + ((addr & ~PMD_MASK));
    }
#elif defined(pmd_sect)
    if (pmd_sect(*pmd)) {
        pr_info("Address 0x%lx maps to a PMD-level huge page\n", addr);
        return __pmd_to_phys(*pmd) + ((addr & ~PMD_MASK));
    }
#endif

    if (pmd_none(*pmd) || pmd_bad(*pmd))
        goto fail;

    pte = pte_offset_kernel(pmd, addr);
    if (!pte)
        goto fail;
    if (!pte_present(*pte))
        goto fail;

    return __pte_to_phys(*pte) + ((addr & ~PAGE_MASK));

fail:
    *err = -ENOENT;
    return 0;
}

// This function appears in 5.14:
// https://github.com/torvalds/linux/commit/fade9c2c6ee2baea7df8e6059b3f143c681e5ce4#diff-fc9ef24572e183c6c049b5ae8029762159787f8669d909452bdf40db748f94a7L52
// https://github.com/torvalds/linux/commit/814b186079cd54d3fe3b6b8ab539cbd44705ef9d#diff-fc9ef24572e183c6c049b5ae8029762159787f8669d909452bdf40db748f94a7R53
// However, it's backport to android13-5.10 but not to android12-5.10.
// https://cs.android.com/android/_/android/kernel/common/+/6d9f07d8f1ffc310a6877153fe882f35ae380799
// So we need to grep kernel source code to detect which one to use.
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 14, 0) || defined(KSU_HAS_NEW_DCACHE_FLUSH)
#define ksu_flush_dcache(start, sz)                                                                                    \
    ({                                                                                                                 \
        unsigned long __start = (start);                                                                               \
        unsigned long __end = __start + (sz);                                                                          \
        dcache_clean_inval_poc(__start, __end);                                                                        \
    })
#define ksu_flush_icache(start, end) caches_clean_inval_pou(start, end)
#else
#define ksu_flush_dcache(start, sz) __flush_dcache_area((void *)start, sz)
#define ksu_flush_icache(start, end) __flush_icache_range(start, end)
#endif

struct patch_text_info {
    void *dst;
    void *src;
    size_t len;
    atomic_t cpu_count;
    int flags;

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 8, 0)
    void *vmap_base;
    void *writable_addr;
#endif
};

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 8, 0)
static int ksu_prepare_patch_vmap(struct patch_text_info *info)
{
    unsigned long addr = (unsigned long)info->dst;
    unsigned long phy;
    unsigned long pfn;
    unsigned long offset;
    struct page *page;
    int phy_err;

    info->vmap_base = NULL;
    info->writable_addr = NULL;

    phy = phys_from_virt(addr, &phy_err);
    if (phy_err) {
        pr_err("failed to find phy addr for patch dst addr 0x%lx\n", addr);
        return phy_err;
    }

    pr_info("phy addr for patch 0x%lx: 0x%lx\n", addr, phy);

    offset = phy & ~PAGE_MASK;

    if (offset + info->len > PAGE_SIZE) {
        pr_err("patch range crosses page boundary: offset=0x%lx len=%zu\n", offset, info->len);
        return -EINVAL;
    }

    pfn = phy >> PAGE_SHIFT;
    if (!pfn_valid(pfn)) {
        pr_err("invalid pfn for patch phy addr 0x%lx\n", phy);
        return -EINVAL;
    }

    page = pfn_to_page(pfn);

    info->vmap_base = vmap(&page, 1, VM_MAP, PAGE_KERNEL);
    if (!info->vmap_base) {
        pr_err("vmap failed for patch phy addr 0x%lx\n", phy);
        return -ENOMEM;
    }

    info->writable_addr = (void *)((unsigned long)info->vmap_base + offset);

    pr_info("vmap addr for patch 0x%lx: base=0x%lx writable=0x%lx\n", addr, (unsigned long)info->vmap_base,
            (unsigned long)info->writable_addr);

    return 0;
}
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 8, 0)
static int ksu_patch_text_nosync_vmap(struct patch_text_info *info)
{
    void *dst = info->dst;
    void *src = info->src;
    size_t len = info->len;
    int flags = info->flags;
    void *map = info->writable_addr;
    int ret;

    pr_info("patch dst=0x%lx src=0x%lx len=%zd via vmap\n", (unsigned long)dst, (unsigned long)src, len);

    memcpy(map, src, len);

    if (memcmp(map, src, len) == 0)
        ret = 0;
    else
        ret = -EFAULT;

    flush_kernel_vmap_range(map, len);

    if (flags & KSU_PATCH_TEXT_FLUSH_ICACHE)
        ksu_flush_icache((uintptr_t)dst, (uintptr_t)dst + len);

    if (flags & KSU_PATCH_TEXT_FLUSH_DCACHE)
        ksu_flush_dcache(dst, len);

    pr_info("patch result=%d\n", ret);
    return ret;
}
#else

// Implementation of arbitrary kernel address modification.
// We could certainly modify the PTE of the target address to make it writable,
// but this would violate the protection mechanisms of some vendor components
// (such as MTK's MKP, see ^1) at higher EL levels. Fortunately, the kernel's
// `aarch64_insn_write` function works fine, which I believe is achieved by
// modifying memory using fixmaps (MKP's kernel module reports the fixmap address
// to its hypervisor, which might be used to determine whether such memory
// modification is "normal" kernel behavior, see ^1). However, we cannot use the
// `aarch64_insn_write` function directly. First, it can only write 4 bytes
// at a time. Secondly, there's a bug in modifying the kernel's rodata (in our
// case, syscall table). The `patch_map` function uses `vmalloc_to_page` to
// obtain the target's physical address, but `vmalloc_to_page` doesn't handle
// huge page mapping correctly (before version 5.13, see ^2).
// Therefore, we need to obtain the target's physical address and use `fixmap` to
// map and poke it manually. Currently, no patch_lock is held, since I think it's
// not a big problem because we are in stop_machine.
// ^1: https://github.com/NothingOSS/android_kernel_device_modules_6.1_nothing_mt6878/blob/957dac185efe46cbf6336b0fff9516d84c8cd78f/drivers/misc/mediatek/mkp/mkp_main.c#L29
// ^2: https://github.com/torvalds/linux/commit/c0eb315ad9719e41ce44708455cc69df7ac9f3f8
static int ksu_patch_text_nosync(void *dst, void *src, size_t len, int flags)
{
    pr_info("patch dst=0x%lx src=0x%lx len=%ld\n", (unsigned long)dst, (unsigned long)src, len);

    unsigned long p = (unsigned long)dst;
    int ret = 0;

    int phy_err;
    unsigned long phy = phys_from_virt(p, &phy_err);
    if (phy_err) {
        ret = phy_err;
        pr_err("failed to find phy addr for patch dst addr 0x%lx\n", p);
        goto err;
    }
    pr_info("phy addr for patch 0x%lx: 0x%lx\n", p, phy);

    void *map = set_fixmap_offset(FIX_TEXT_POKE0, phy);
    pr_info("fixmap addr for patch 0x%lx: 0x%lx\n", p, (unsigned long)map);

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    // bypass some stupid GKI kernel's self protection
    ret = (int)copy_to_kernel_nofault(map, src, len);
#else
    memcpy(map, src, len);

    if (memcmp(map, src, len) == 0) {
        ret = 0;
    } else {
        ret = -EFAULT;
    }
#endif

    clear_fixmap(FIX_TEXT_POKE0);

    if (flags & KSU_PATCH_TEXT_FLUSH_ICACHE)
        ksu_flush_icache((uintptr_t)dst, (uintptr_t)dst + len);
    if (flags & KSU_PATCH_TEXT_FLUSH_DCACHE)
        ksu_flush_dcache(dst, len);

err:
    pr_info("patch result=%d\n", ret);
    return ret;
}

#endif

static int ksu_patch_text_cb(void *arg)
{
    struct patch_text_info *pp = arg;
    void *dst = pp->dst, *src = pp->src;
    size_t len = pp->len;
    int flags = pp->flags;

    int ret = 0;

    /* The last CPU becomes master */
    if (atomic_inc_return(&pp->cpu_count) == num_online_cpus()) {
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 8, 0)
        ret = ksu_patch_text_nosync_vmap(pp);
#else
        ret = ksu_patch_text_nosync(dst, src, len, flags);
#endif
        /* Notify other processors with an additional increment. */
        atomic_inc(&pp->cpu_count);
    } else {
        while (atomic_read(&pp->cpu_count) <= num_online_cpus())
            cpu_relax();
        isb();
    }

    return ret;
}

int ksu_patch_text(void *dst, void *src, size_t len, int flags)
{
    struct patch_text_info info = {
        .dst = dst,
        .src = src,
        .len = len,
        .cpu_count = ATOMIC_INIT(0),
        .flags = flags,
#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 8, 0)
        .vmap_base = NULL,
        .writable_addr = NULL,
#endif
    };

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 8, 0)
    int ret;

    ret = ksu_prepare_patch_vmap(&info);
    if (ret)
        return ret;

    ret = stop_machine(ksu_patch_text_cb, &info, cpu_online_mask);

    vunmap(info.vmap_base);

    return ret;
#else
    return stop_machine(ksu_patch_text_cb, &info, cpu_online_mask);
#endif
}

#endif /* __aarch64__ */