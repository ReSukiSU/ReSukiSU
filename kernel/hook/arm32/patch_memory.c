/* SPDX-License-Identifier: GPL-2.0-only */
/*
 * Copyright (C) 2023 bmax121. All Rights Reserved.
 */

#ifdef __arm__

#include <linux/cache.h>
#include "../patch_memory.h"
#include "klog.h" // IWYU pragma: keep

#include <asm/pgtable.h>
#include <asm/page.h>

#include <linux/cpumask.h>
#include <linux/gfp.h> // IWYU pragma: keep
#include <linux/uaccess.h>
#include <linux/stop_machine.h>
#include <asm/cacheflush.h>
#include <asm/barrier.h>

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 8, 0)
#include <asm/fixmap.h>
#else
#include <linux/vmalloc.h>
#include <linux/mm.h>
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 8, 0) && !defined(CONFIG_ARM_LPAE)
#include <asm/pgtable-hwdef.h>
#endif

#include <linux/sched.h>

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
#define KSU_HAVE_P4D
// --- Architecture-specific Page Table to Physical Address translation ---
#ifndef KSU_P4D_TO_PHYS
#define KSU_P4D_TO_PHYS(p4d) ((unsigned long)p4d_pfn(p4d) << PAGE_SHIFT)
#endif
#endif

#ifndef KSU_PUD_TO_PHYS
#define KSU_PUD_TO_PHYS(pud) ((unsigned long)pud_pfn(pud) << PAGE_SHIFT)
#endif
#ifndef KSU_PMD_TO_PHYS
#define KSU_PMD_TO_PHYS(pmd) ((unsigned long)pmd_pfn(pmd) << PAGE_SHIFT)
#endif
#ifndef KSU_PTE_TO_PHYS
#define KSU_PTE_TO_PHYS(pte) ((unsigned long)pte_pfn(pte) << PAGE_SHIFT)
#endif

// Translate a kernel virtual address to a physical address by walking the
// init_mm page tables.
unsigned long phys_from_virt(unsigned long addr, int *err)
{
    struct mm_struct *mm = &init_mm;
    pgd_t *pgd;
#ifdef KSU_HAVE_P4D
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

#ifdef KSU_HAVE_P4D
    p4d = p4d_offset(pgd, addr);
    if (p4d_none(*p4d) || p4d_bad(*p4d))
        goto fail;
    pr_info("p4d of 0x%lx p=0x%lx v=0x%lx", addr, (uintptr_t)p4d, (uintptr_t)p4d_val(*p4d));
#if defined(p4d_leaf)
    if (p4d_leaf(*p4d)) {
        pr_info("Address 0x%lx maps to a P4D-level huge page\n", addr);
        return KSU_P4D_TO_PHYS(*p4d) + ((addr & ~P4D_MASK));
    }
#elif defined(p4d_large)
    if (p4d_large(*p4d)) {
        return KSU_P4D_TO_PHYS(*p4d) + ((addr & ~P4D_MASK));
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
        return KSU_PUD_TO_PHYS(*pud) + ((addr & ~PUD_MASK));
    }
#elif defined(pud_large)
    if (pud_large(*pud)) {
        return KSU_PUD_TO_PHYS(*pud) + ((addr & ~PUD_MASK));
    }
#endif

    pmd = pmd_offset(pud, addr);
    pr_info("pmd of 0x%lx p=0x%lx v=0x%lx", addr, (uintptr_t)pmd, (uintptr_t)pmd_val(*pmd));

#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 8, 0) && !defined(CONFIG_ARM_LPAE)
    /*
     * ARM32 non-LPAE short-descriptor section mapping.
     *
     * Linux folds two 1MB hardware PMD section descriptors into one
     * 2MB logical PGD/PMD slot, so select the correct half first.
     */
    {
        pmd_t *sect_pmd = pmd + ((addr >> SECTION_SHIFT) & 1);
        unsigned long sect_val = (unsigned long)pmd_val(*sect_pmd);

        pr_info("arm non-lpae sect probe 0x%lx p=0x%lx v=0x%lx", addr, (uintptr_t)sect_pmd, sect_val);

        if ((sect_val & PMD_TYPE_MASK) == PMD_TYPE_SECT) {
            pr_info("Address 0x%lx maps to ARM non-LPAE section\n", addr);

            return (sect_val & SECTION_MASK) + (addr & ~SECTION_MASK);
        }
    }
#endif

#if defined(pmd_leaf)
    if (pmd_leaf(*pmd)) {
        pr_info("Address 0x%lx maps to a PMD-level huge page\n", addr);
        return KSU_PMD_TO_PHYS(*pmd) + ((addr & ~PMD_MASK));
    }
#elif defined(pmd_large)
    if (pmd_large(*pmd)) {
        return KSU_PMD_TO_PHYS(*pmd) + ((addr & ~PMD_MASK));
    }
#elif defined(pmd_sect)
    if (pmd_sect(*pmd)) {
        pr_info("Address 0x%lx maps to a PMD-level section page\n", addr);
        return KSU_PMD_TO_PHYS(*pmd) + ((addr & ~PMD_MASK));
    }
#endif

    if (pmd_none(*pmd) || pmd_bad(*pmd))
        goto fail;

    pte = pte_offset_kernel(pmd, addr);
    if (!pte)
        goto fail;
    if (!pte_present(*pte))
        goto fail;

    return KSU_PTE_TO_PHYS(*pte) + ((addr & ~PAGE_MASK));

fail:
    *err = -ENOENT;
    return 0;
}

#define ksu_flush_dcache(start, sz)                                                                                    \
    do {                                                                                                               \
    } while (0)
#define ksu_flush_icache(start, end) flush_icache_range((unsigned long)(start), (unsigned long)(end))
#define ksu_isb() isb()

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

    flush_icache_range((unsigned long)dst, (unsigned long)dst + len);

    if (flags & KSU_PATCH_TEXT_FLUSH_ICACHE)
        ksu_flush_icache((uintptr_t)dst, (uintptr_t)dst + len);

    if (flags & KSU_PATCH_TEXT_FLUSH_DCACHE)
        ksu_flush_dcache(dst, len);

    pr_info("patch result=%d\n", ret);
    return ret;
}
#else
static int ksu_patch_text_nosync(void *dst, void *src, size_t len, int flags)
{
    pr_info("patch dst=0x%lx src=0x%lx len=%zd\n", (unsigned long)dst, (unsigned long)src, len);

    unsigned long p = (unsigned long)dst;
    int ret = 0;
    void *map;

    int phy_err;
    unsigned long phy = phys_from_virt(p, &phy_err);
    if (phy_err) {
        ret = phy_err;
        pr_err("failed to find phy addr for patch dst addr 0x%lx\n", p);
        goto err;
    }
    pr_info("phy addr for patch 0x%lx: 0x%lx\n", p, phy);

#if defined(FIX_TEXT_POKE0)
    set_fixmap(FIX_TEXT_POKE0, phy & PAGE_MASK);
    map = (void *)(fix_to_virt(FIX_TEXT_POKE0) + (phy & ~PAGE_MASK));
#else
    // Fallback for kernels that don't have FIX_TEXT_POKE0 exposed
    set_fixmap(FIX_BTMAP_BEGIN, phy & PAGE_MASK);
    map = (void *)(fix_to_virt(FIX_BTMAP_BEGIN) + (phy & ~PAGE_MASK));
#endif

    pr_info("fixmap addr for patch 0x%lx: 0x%lx\n", p, (unsigned long)map);

    memcpy(map, src, len);

    if (memcmp(map, src, len) == 0) {
        ret = 0;
    } else {
        ret = -EFAULT;
    }

    flush_kernel_vmap_range(map, len);

#if defined(FIX_TEXT_POKE0)
    clear_fixmap(FIX_TEXT_POKE0);
#else
    clear_fixmap(FIX_BTMAP_BEGIN);
#endif

    flush_icache_range((unsigned long)dst, (unsigned long)dst + len);

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
        ksu_isb();
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

#endif /* __arm__ */