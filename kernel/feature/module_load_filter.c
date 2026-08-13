#include <asm/elf.h>

#include <linux/elf.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/slab.h>
#include <linux/string.h>
#include <linux/uaccess.h>
#include <uapi/linux/module.h>

#include "compat/kernel_compat.h"
#include "feature/module_load_filter.h"
#include "klog.h" // IWYU pragma: keep
#include "arch.h"
#ifdef CONFIG_KSU_TRACEPOINT_HOOK
#include "hook/syscall_hook.h"
#endif

// This is a load-time policy, not an attempt to infer whether arbitrary
// modules are safe. Userspace can pass device-specific .modinfo names through
// blocked_preset_modules=foo,bar. An empty value disables the policy.

struct ksu_module_name {
    const char *name;
    size_t len;
};

// Bounds for ELF metadata parsing. Module text/data can be large, but only the
// header, section table, section-name table and .modinfo are needed here.
#define KSU_MAX_SECTIONS 512
#define KSU_MAX_SHSTRTAB (64 * 1024)
#define KSU_MAX_MODINFO (16 * 1024)

// Abstract source of an ELF module image. init_module(2) passes a userspace
// buffer; finit_module(2) passes a file descriptor. Both are read at explicit
// offsets so we never have to slurp the entire (large) module into memory.
struct ksu_elf_reader {
    const char __user *umod; // init_module source, NULL for the file path
    unsigned long umod_len;
    struct file *file; // finit_module source, NULL for the userspace buffer
    loff_t file_size;
};

// Read exactly @len bytes at @offset into @buf, with full bounds checking.
// Returns 0 on success, negative on any error (so callers fail open).
static int ksu_reader_read(struct ksu_elf_reader *r, loff_t offset, void *buf, size_t len)
{
    loff_t total = r->umod ? (loff_t)r->umod_len : r->file_size;

    if (len == 0)
        return -EINVAL;
    if (offset < 0 || (loff_t)len > total || offset > total - (loff_t)len)
        return -ERANGE;

    if (r->umod) {
        if (copy_from_user(buf, r->umod + offset, len))
            return -EFAULT;
        return 0;
    }

    loff_t pos = offset;
    ssize_t n = ksu_kernel_read_compat(r->file, buf, len, &pos);
    if (n < 0 || (size_t)n != len)
        return -EIO;
    return 0;
}

// Kernel module names never contain '-'; modpost rewrites it to '_'. Userspace
// is allowed to configure the filename spelling, so fold it on that side too.
static char ksu_normalize_module_char(char ch)
{
    return ch == '-' ? '_' : ch;
}

static bool ksu_name_matches(const char *name, size_t name_len, const char *configured, size_t configured_len,
                             bool normalize_filename)
{
    size_t i;

    if (name_len != configured_len)
        return false;

    for (i = 0; i < name_len; i++) {
        char ch = normalize_filename ? ksu_normalize_module_char(name[i]) : name[i];

        if (ch != ksu_normalize_module_char(configured[i]))
            return false;
    }

    return true;
}

static bool ksu_find_blocked_module(const char *name, size_t name_len, bool normalize_filename,
                                    struct ksu_module_name *blocked)
{
    const char *cursor = ksu_blocked_preset_modules;
    const char *end = cursor + strnlen(cursor, sizeof(ksu_blocked_preset_modules));

    while (cursor < end) {
        const char *separator = memchr(cursor, ',', end - cursor);
        size_t entry_len = separator ? (size_t)(separator - cursor) : (size_t)(end - cursor);

        if (entry_len && ksu_name_matches(name, name_len, cursor, entry_len, normalize_filename)) {
            blocked->name = cursor;
            blocked->len = entry_len;
            return true;
        }

        if (!separator)
            break;
        cursor = separator + 1;
    }

    return false;
}

#ifdef MODULE_INIT_COMPRESSED_FILE
static bool ksu_has_suffix(const char *name, size_t name_len, const char *suffix, size_t suffix_len)
{
    return name_len > suffix_len && !memcmp(name + name_len - suffix_len, suffix, suffix_len);
}

static bool ksu_get_blocked_compressed_module(struct file *file, struct ksu_module_name *blocked)
{
    static const char *const compression_suffixes[] = { ".gz", ".xz", ".zst" };
    const struct qstr *filename = &file->f_path.dentry->d_name;
    size_t name_len = filename->len;
    size_t i;

    for (i = 0; i < ARRAY_SIZE(compression_suffixes); i++) {
        const char *suffix = compression_suffixes[i];
        size_t suffix_len = strlen(suffix);

        if (ksu_has_suffix(filename->name, name_len, suffix, suffix_len)) {
            name_len -= suffix_len;
            break;
        }
    }

    if (i == ARRAY_SIZE(compression_suffixes) || !ksu_has_suffix(filename->name, name_len, ".ko", 3))
        return false;

    name_len -= 3;
    return ksu_find_blocked_module(filename->name, name_len, true, blocked);
}
#endif

static bool ksu_is_block_module(struct ksu_elf_reader *r, struct ksu_module_name *blocked)
{
    Elf_Ehdr ehdr;
    Elf_Shdr *shdrs = NULL;
    char *shstrtab = NULL;
    char *modinfo = NULL;
    Elf_Shdr *shstr_sh;
    Elf_Shdr *modinfo_sh = NULL;
    unsigned int shnum, i;
    unsigned long shtab_bytes;
    bool should_block = false;

    if (ksu_reader_read(r, 0, &ehdr, sizeof(ehdr)))
        return false;

    if (memcmp(ehdr.e_ident, ELFMAG, SELFMAG) != 0)
        return false;
    if (ehdr.e_ident[EI_CLASS] != ELF_CLASS || ehdr.e_ident[EI_DATA] != ELF_DATA ||
        ehdr.e_ident[EI_VERSION] != EV_CURRENT)
        return false;
    if (ehdr.e_type != ET_REL || !elf_check_arch(&ehdr) || ehdr.e_version != EV_CURRENT)
        return false;
    if (ehdr.e_shentsize != sizeof(Elf_Shdr))
        return false;

    shnum = ehdr.e_shnum;
    if (shnum == 0 || shnum > KSU_MAX_SECTIONS)
        return false;
    if (ehdr.e_shstrndx >= shnum)
        return false;

    shtab_bytes = (unsigned long)shnum * sizeof(Elf_Shdr);
    shdrs = kmalloc(shtab_bytes, GFP_KERNEL);
    if (!shdrs)
        return false;
    if (ksu_reader_read(r, ehdr.e_shoff, shdrs, shtab_bytes))
        goto out;

    // Read the section-name string table.
    shstr_sh = &shdrs[ehdr.e_shstrndx];
    if (shstr_sh->sh_type != SHT_STRTAB || shstr_sh->sh_size == 0 || shstr_sh->sh_size > KSU_MAX_SHSTRTAB)
        goto out;
    shstrtab = kmalloc(shstr_sh->sh_size, GFP_KERNEL);
    if (!shstrtab)
        goto out;
    if (ksu_reader_read(r, shstr_sh->sh_offset, shstrtab, shstr_sh->sh_size))
        goto out;

    // Locate the .modinfo section.
    for (i = 0; i < shnum; i++) {
        Elf_Shdr *sh = &shdrs[i];
        const char *name;
        unsigned long remaining;

        if (sh->sh_name >= shstr_sh->sh_size)
            continue;
        name = shstrtab + sh->sh_name;
        remaining = shstr_sh->sh_size - sh->sh_name;
        if (strnlen(name, remaining) >= remaining)
            continue; // not NUL-terminated within bounds
        if (sh->sh_type == SHT_PROGBITS && strcmp(name, ".modinfo") == 0) {
            modinfo_sh = sh;
            break;
        }
    }
    if (!modinfo_sh)
        goto out;

    if (modinfo_sh->sh_size == 0 || modinfo_sh->sh_size > KSU_MAX_MODINFO)
        goto out;
    modinfo = kmalloc(modinfo_sh->sh_size, GFP_KERNEL);
    if (!modinfo)
        goto out;
    if (ksu_reader_read(r, modinfo_sh->sh_offset, modinfo, modinfo_sh->sh_size))
        goto out;

    // .modinfo is a sequence of NUL-separated "key=value" entries. Module
    // names are compared exactly against the configured load-time list.
    {
        const char *p = modinfo;
        const char *end = modinfo + modinfo_sh->sh_size;

        while (p < end) {
            const char *nul = memchr(p, '\0', end - p);
            size_t entlen;

            if (!nul)
                break;
            entlen = nul - p;
            if (entlen > 5 && memcmp(p, "name=", 5) == 0) {
                const char *val = p + 5;
                size_t vlen = entlen - 5;

                should_block = ksu_find_blocked_module(val, vlen, false, blocked);
                break; // The module name is unique.
            }
            p = nul + 1;
        }
    }

out:
    kfree(modinfo);
    kfree(shstrtab);
    kfree(shdrs);
    return should_block;
}

static bool ksu_get_blocked_file_module(struct file *file, int flags, struct ksu_module_name *blocked)
{
    struct ksu_elf_reader r = {
        .umod = NULL,
        .umod_len = 0,
        .file = file,
        .file_size = i_size_read(file_inode(file)),
    };

    // https://github.com/torvalds/linux/commit/b1ae6dc41eaaa98bb75671e0f3665bfda248c3e7
    // linux kernel 5.17+
#ifdef MODULE_INIT_COMPRESSED_FILE
    if (flags & MODULE_INIT_COMPRESSED_FILE)
        return ksu_get_blocked_compressed_module(file, blocked);
#else
    (void)flags;
#endif

    return r.file_size >= (loff_t)sizeof(Elf_Ehdr) && ksu_is_block_module(&r, blocked);
}

int ksu_handle_init_module(const void __user *umod, unsigned long umod_len)
{
    struct ksu_module_name blocked = { 0 };
    struct ksu_elf_reader r = {
        .umod = (const char __user *)umod,
        .umod_len = umod_len,
        .file = NULL,
        .file_size = 0,
    };

    if (!ksu_blocked_preset_modules[0])
        return KSU_MODULE_LOAD_CONTINUE;

    if (r.umod && r.umod_len >= sizeof(Elf_Ehdr) && ksu_is_block_module(&r, &blocked)) {
        pr_info("module_load_filter: block %.*s load due to it in blocklist\n", (int)blocked.len, blocked.name);
        return 0;
    }

    return KSU_MODULE_LOAD_CONTINUE;
}

int ksu_handle_finit_module(int fd, int flags)
{
    struct ksu_module_name blocked = { 0 };
    struct file *file;
    bool should_block;

    if (!ksu_blocked_preset_modules[0])
        return KSU_MODULE_LOAD_CONTINUE;

    file = fget(fd);

    if (!file)
        return KSU_MODULE_LOAD_CONTINUE;

    // @blocked points into ksu_blocked_preset_modules, so it stays valid here.
    should_block = ksu_get_blocked_file_module(file, flags, &blocked);
    fput(file);

    if (should_block) {
        pr_info("module_load_filter: block %.*s load due to it in blocklist\n", (int)blocked.len, blocked.name);
        return 0;
    }

    return KSU_MODULE_LOAD_CONTINUE;
}

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
// init_module(2): sys_init_module(void __user *umod, unsigned long len,
//                                const char __user *uargs)
static long (*orig_sys_init_module)(const struct pt_regs *regs);
static long ksu_sys_init_module(const struct pt_regs *regs)
{
    int ret = ksu_handle_init_module((const void __user *)PT_REGS_PARM1(regs), (unsigned long)PT_REGS_PARM2(regs));

    if (ret != KSU_MODULE_LOAD_CONTINUE)
        return ret;

    return orig_sys_init_module(regs);
}

// finit_module(2): sys_finit_module(int fd, const char __user *uargs, int flags)
static long (*orig_sys_finit_module)(const struct pt_regs *regs);
static long ksu_sys_finit_module(const struct pt_regs *regs)
{
    int ret = ksu_handle_finit_module((int)PT_REGS_PARM1(regs), (int)PT_REGS_PARM3(regs));

    if (ret != KSU_MODULE_LOAD_CONTINUE)
        return ret;

    return orig_sys_finit_module(regs);
}
#endif

void __init ksu_module_load_filter_hook_init(void)
{
    if (!ksu_blocked_preset_modules[0]) {
        pr_info("module_load_filter: no modules should be blocked\n");
        return;
    }

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
    // userspace use __NR_init_module / __NR_finit_module to load kernel modules
    ksu_syscall_table_hook(__NR_init_module, ksu_sys_init_module, &orig_sys_init_module);
    ksu_syscall_table_hook(__NR_finit_module, ksu_sys_finit_module, &orig_sys_finit_module);
#endif
    pr_info("module_load_filter: target modules: %s\n", ksu_blocked_preset_modules);
}

void __exit ksu_module_load_filter_hook_exit(void)
{
    if (!ksu_blocked_preset_modules[0])
        return;

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
    ksu_syscall_table_unhook(__NR_init_module);
    ksu_syscall_table_unhook(__NR_finit_module);
#endif
}
