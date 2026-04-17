#include <linux/kthread.h>
#include <linux/syslog.h>
#include <linux/vmalloc.h>

#include "apatch_conflict.h"
#include "klog.h"

struct kp_symbol_region {
    uint64_t symbol_start;
    uint64_t symbol_end;
};

u8 kernel_patch_type = 0;
static struct kp_symbol_region symbol_region = { 0, 0 };

// yep, read logs is an bad idea, but unless that,
// we should try to find the KP syscall callback/inline hook callback from __NR_supercall
// and search 64KiB memorys
// https://github.com/bmax121/KernelPatch/blob/ece212d25a56dc308c8b230505cb664688abf2f1/kernel/kpimg.lds#L80
// https://github.com/bmax121/KernelPatch/blob/ece212d25a56dc308c8b230505cb664688abf2f1/kernel/base/symbol.c#L58

static bool try_dump_kp_symbol_addr()
{
    bool ret = false;
    char *buf;
    int len;
    char *match;
    int log_size;

    log_size = do_syslog(SYSLOG_ACTION_SIZE_BUFFER, NULL, 0, 0);

    if (log_size <= 0) {
        log_size = 256 * 1024;
    }
    buf = vmalloc(log_size);
    if (!buf)
        return false;

    len = do_syslog(SYSLOG_ACTION_READ_ALL, buf, log_size, 0);

    pr_info("pulled log size: %d", log_size);

    if (len > 0) {
        buf[len < log_size ? len : log_size - 1] = '\0';

        match = strstr(buf, "KP Symbol:");

        if (match) {
            int parsed = sscanf(match, "KP Symbol: %llx, %llx", &symbol_region.symbol_start, &symbol_region.symbol_end);

            if (parsed == 2) {
                pr_info("Successfully parsed kp symbols!\n");
                pr_info("KP Symbol Start: 0x%llx, End: 0x%llx\n", symbol_region.symbol_start, symbol_region.symbol_end);
                ret = true;
            } else {
                pr_err("Found kp symbol debug log but failed to parse addresses (parsed: %d)\n", parsed);
            }
        } else {
            pr_info("KP symbols not found from logs.\n");
        }
    }

    vfree(buf);
    return ret;
}

// https://github.com/bmax121/KernelPatch/blob/ece212d25a56dc308c8b230505cb664688abf2f1/kernel/base/symbol.c#L18-L52
// https://github.com/bmax121/KernelPatch/blob/ece212d25a56dc308c8b230505cb664688abf2f1/kernel/include/symbol.h#L11-L17
// Copyright (C) 2023 bmax121. All Rights Reserved.

// todo: name len
#define KP_SYMBOL_LEN 32
typedef struct {
    unsigned long addr;
    unsigned long hash;
    const char name[KP_SYMBOL_LEN];
} kp_symbol_t;

// DJB2
static unsigned long sym_hash(const char *str)
{
    unsigned long hash = 5381;
    int c;
    while ((c = *str++)) {
        hash = ((hash << 5) + hash) + c;
    }
    return hash;
}

static int local_strcmp(const char *s1, const char *s2)
{
    const unsigned char *c1 = (const unsigned char *)s1;
    const unsigned char *c2 = (const unsigned char *)s2;
    unsigned char ch;
    int d = 0;
    while (1) {
        d = (int)(ch = *c1++) - (int)*c2++;
        if (d || !ch)
            break;
    }
    return d;
}

static unsigned long symbol_lookup_name(const char *name)
{
    unsigned long hash = sym_hash(name);
    uint64_t addr;
    for (addr = symbol_region.symbol_start; addr < symbol_region.symbol_end; addr += sizeof(kp_symbol_t)) {
        kp_symbol_t *symbol = (kp_symbol_t *)addr;
        if (hash == symbol->hash && !local_strcmp(name, symbol->name)) {
            return symbol->addr;
        }
    }
    return 0;
}

static int detect_conflict_thread(void *data)
{
    pr_info("Start checking KernelPatch...");
    if (!try_dump_kp_symbol_addr()) {
        pr_info("KernelPatch was not found");
        kernel_patch_type = KERNEL_PATCH_NOT_FOUND;
        return 0;
    }

    // original kp have full sucompat api
    // e.g su_get_path
    if (symbol_lookup_name("su_get_path")) {
        pr_info("Original KernelPatch was found.");
        kernel_patch_type = KERNEL_PATCH_ORIGINAL;
        return 0;
    }

    // kpn doesn't have su_get_path but have is_su_allow_uid
    if (symbol_lookup_name("is_su_allow_uid")) {
        pr_info("KPatch-Next was found.");
        kernel_patch_type = KERNEL_PATCH_KPN;
        return 0;
    }

    // sukisu kernel patch patch doesn't have any sucompat apis
    pr_info("SukiSU KernelPatch Patch was found.");
    kernel_patch_type = KERNEL_PATCH_SUKISU;
    return 0;
}

void ksu_start_apatch_conflict_check()
{
    kthread_run(detect_conflict_thread, NULL, "detect_apatch_conflict");
}