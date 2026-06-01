#include <linux/err.h>
#include <linux/fs.h>
#include <linux/gfp.h>
#include <linux/kernel.h>
#include <linux/slab.h>
#include <linux/version.h>
#ifdef CONFIG_KSU_DEBUG
#include <linux/moduleparam.h>
#endif
#include <crypto/hash.h>
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 11, 0)
#include <crypto/sha2.h>
#else
#include <crypto/sha.h>
#endif
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 4, 0)
#include <linux/hex.h>
#endif

#include "manager/apk_sign.h"
#include "manager/manager_identity.h"
#include "policy/app_profile.h"
#include "feature/dynamic_manager.h"
#include "klog.h" // IWYU pragma: keep
#include "manager_sign.h"
#include "compat/kernel_compat.h"

struct sdesc {
    struct shash_desc shash;
    char ctx[];
};

static apk_sign_key_t apk_sign_keys[] = {
    { EXPECTED_SIZE_RESUKISU, EXPECTED_HASH_RESUKISU }, /* ReSukiSU/ReSukiSU */
#ifdef CONFIG_KSU_MULTI_MANAGER_SUPPORT
    { EXPECTED_SIZE_OFFICIAL, EXPECTED_HASH_OFFICIAL }, // tiann/KernelSU
    { EXPECTED_SIZE_5EC1CFF, EXPECTED_HASH_5EC1CFF }, // 5ec1cff/KernelSU
    { EXPECTED_SIZE_RSUNTK, EXPECTED_HASH_RSUNTK }, // rsuntk/KernelSU
    { EXPECTED_SIZE_SUKISU, EXPECTED_HASH_SUKISU }, // SukiSU-Ultra/SukiSU-Ultra
    { EXPECTED_SIZE_KOWX712, EXPECTED_HASH_KOWX712 }, // KOWX712/KernelSU
#ifdef EXPECTED_SIZE
    { EXPECTED_SIZE, EXPECTED_HASH }, // Custom
#endif
#ifdef EXPECTED_PR_BUILD_SIZE
    { EXPECTED_PR_BUILD_SIZE, EXPECTED_PR_BUILD_HASH }, // Custom 2 (For PR build)
#endif
#endif
};

static struct sdesc *init_sdesc(struct crypto_shash *alg)
{
    struct sdesc *sdesc;
    int size;

    size = sizeof(struct shash_desc) + crypto_shash_descsize(alg);
    sdesc = kzalloc(size, GFP_KERNEL);
    if (!sdesc)
        return ERR_PTR(-ENOMEM);
    sdesc->shash.tfm = alg;
    return sdesc;
}

static int calc_hash(struct crypto_shash *alg, const unsigned char *data, unsigned int datalen, unsigned char *digest)
{
    struct sdesc *sdesc;
    int ret;

    sdesc = init_sdesc(alg);
    if (IS_ERR(sdesc)) {
        pr_info("can't alloc sdesc\n");
        return PTR_ERR(sdesc);
    }

    ret = crypto_shash_digest(&sdesc->shash, data, datalen, digest);
    kfree(sdesc);
    return ret;
}

static int ksu_sha256(const unsigned char *data, unsigned int datalen, unsigned char *digest)
{
    struct crypto_shash *alg;
    char *hash_alg_name = "sha256";
    int ret;

    alg = crypto_alloc_shash(hash_alg_name, 0, 0);
    if (IS_ERR(alg)) {
        pr_info("can't alloc alg %s\n", hash_alg_name);
        return PTR_ERR(alg);
    }
    ret = calc_hash(alg, data, datalen, digest);
    crypto_free_shash(alg);
    return ret;
}

/* 【调整到上方】公共校验函数，完美同步支持内置多管家、自定义大小、动态管家 */
static bool verify_cert_hash(const char *hash_str, u32 cert_size, u8 *matched_index)
{
    u8 i;
    apk_sign_key_t sign_key;

    // keep 255, 254, 253 here
    BUILD_BUG_ON(ARRAY_SIZE(apk_sign_keys) >= 253);
    for (i = 0; i < ARRAY_SIZE(apk_sign_keys); i++) {
        sign_key = apk_sign_keys[i];
        if (cert_size == sign_key.size && strcmp(sign_key.sha256, hash_str) == 0) {
            if (matched_index)
                *matched_index = i;
            return true;
        }
    }

    if (ksu_is_dynamic_manager_enabled()) {
        sign_key = ksu_get_dynamic_manager_sign();
        if (cert_size == sign_key.size && strcmp(sign_key.sha256, hash_str) == 0) {
            if (matched_index)
                *matched_index = KSU_SIGNATURE_INDEX_DYNAMIC_MANAGER;
            return true;
        }
    }

    return false;
}

static bool check_block(struct file *fp, u32 *size4, loff_t *pos, u32 *offset, u8 *matched_index)
{
    bool signature_valid = false;
    unsigned char digest[SHA256_DIGEST_SIZE];
    char hash_str[SHA256_DIGEST_SIZE * 2 + 1];
#define CERT_MAX_LENGTH 1024
    char cert[CERT_MAX_LENGTH];

    ksu_kernel_read_compat(fp, size4, 0x4, pos); // signer-sequence length
    ksu_kernel_read_compat(fp, size4, 0x4, pos); // signer length
    ksu_kernel_read_compat(fp, size4, 0x4, pos); // signed data length
    *offset += 0x4 * 3;

    ksu_kernel_read_compat(fp, size4, 0x4, pos); // digests-sequence length
    *pos += *size4;
    *offset += 0x4 + *size4;

    ksu_kernel_read_compat(fp, size4, 0x4, pos); // certificates length
    ksu_kernel_read_compat(fp, size4, 0x4, pos); // certificate length
    *offset += 0x4 * 2;

    if (*size4 > CERT_MAX_LENGTH) {
        pr_info("cert length overlimit: %u\n", *size4);
        return false;
    }

    if (ksu_kernel_read_compat(fp, cert, *size4, pos) != *size4)
        return false;

    if (ksu_sha256(cert, *size4, digest) < 0) {
        pr_err("sha256 error\n");
        return false;
    }
    bin2hex(hash_str, digest, SHA256_DIGEST_SIZE);
    hash_str[SHA256_DIGEST_SIZE * 2] = '\0';

    // 【接入同步方案】v2 签名同样调用统一的公共校验函数
    signature_valid = verify_cert_hash(hash_str, *size4, matched_index);

    *offset += *size4;

    return signature_valid;
}

struct zip_entry_header {
    uint32_t signature;
    uint16_t version;
    uint16_t flags;
    uint16_t compression;
    uint16_t mod_time;
    uint16_t mod_date;
    uint32_t crc32;
    uint32_t compressed_size;
    uint32_t uncompressed_size;
    uint16_t file_name_length;
    uint16_t extra_field_length;
} __attribute__((packed));

struct zip_data_descriptor {
    uint32_t crc32;
    uint32_t compressed_size;
    uint32_t uncompressed_size;
} __attribute__((packed));

static bool check_v1_signature(char *path, u8 *signature_index)
{
    struct file *fp;
    struct zip_entry_header header;
    loff_t pos = 0;
    bool v1_signing_valid = false;
    unsigned char digest[SHA256_DIGEST_SIZE];
    char hash_str[SHA256_DIGEST_SIZE * 2 + 1];
    char *cert_buf = NULL;

    fp = filp_open(path, O_RDONLY, 0);
    if (IS_ERR(fp)) {
        pr_err("open %s error for v1 check.\n", path);
        return false;
    }
    fp->f_mode |= FMODE_NONOTIFY;

    while (ksu_kernel_read_compat(fp, &header, sizeof(struct zip_entry_header), &pos) ==
           sizeof(struct zip_entry_header)) {
        
        if (header.signature != 0x04034b50) {
            break; 
        }

        if (header.file_name_length > 0 && header.file_name_length < 256) {
            char fileName[256];
            ksu_kernel_read_compat(fp, fileName, header.file_name_length, &pos);
            fileName[header.file_name_length] = '\0';

            if (strncasecmp(fileName, "META-INF/", 9) == 0 &&
                (strncasecmp(fileName + header.file_name_length - 4, ".RSA", 4) == 0 ||
                 strncasecmp(fileName + header.file_name_length - 4, ".DSA", 4) == 0 ||
                 strncasecmp(fileName + header.file_name_length - 3, ".EC", 3) == 0)) {

                pos += header.extra_field_length;

                if (header.compressed_size > 0 && header.compressed_size < 8192 && 
                    header.compression == 0) { 
                    
                    cert_buf = kmalloc(header.compressed_size, GFP_KERNEL);
                    if (!cert_buf)
                        goto clean;

                    if (ksu_kernel_read_compat(fp, cert_buf, header.compressed_size, &pos) == header.compressed_size) {
                        if (ksu_sha256(cert_buf, header.compressed_size, digest) >= 0) {
                            bin2hex(hash_str, digest, SHA256_DIGEST_SIZE);
                            hash_str[SHA256_DIGEST_SIZE * 2] = '\0';

                            // 调用公共函数
                            if (verify_cert_hash(hash_str, header.compressed_size, signature_index)) {
                                v1_signing_valid = true;
                                kfree(cert_buf);
                                break; 
                            }
                        }
                    }
                    kfree(cert_buf);
                    cert_buf = NULL;
                } else {
                    pos += header.compressed_size;
                }
            } else {
                pos += header.extra_field_length + header.compressed_size;
            }
        } else {
            pos += header.extra_field_length + header.compressed_size;
        }

        if (header.flags & 0x0008) {
            uint32_t sig;
            if (ksu_kernel_read_compat(fp, &sig, 4, &pos) == 4) {
                if (sig == 0x08074b50) { 
                    pos += sizeof(struct zip_data_descriptor);
                } else {
                    pos -= 4;
                    pos += 12;
                }
            }
        }
    }

clean:
    filp_close(fp, 0);
    return v1_signing_valid;
}

static __always_inline bool check_v2_signature(char *path, u8 *signature_index)
{
    unsigned char buffer[0x11] = { 0 };
    u32 size4;
    u64 size8, size_of_block;

    loff_t pos;

    bool v2_signing_valid = false;
    int v2_signing_blocks = 0;
    bool v3_signing_exist = false;
    bool v3_1_signing_exist = false;
    u8 matched_index = -1;
    int i;
    struct file *fp = filp_open(path, O_RDONLY, 0);
    if (IS_ERR(fp)) {
        pr_err("open %s error.\n", path);
        return false;
    }

    fp->f_mode |= FMODE_NONOTIFY;

    for (i = 0;; ++i) {
        unsigned short n;
        pos = generic_file_llseek(fp, -i - 2, SEEK_END);
        ksu_kernel_read_compat(fp, &n, 2, &pos);
        if (n == i) {
            pos -= 22;
            ksu_kernel_read_compat(fp, &size4, 4, &pos);
            if ((size4 ^ 0xcafebabeu) == 0xccfbf1eeu) {
                break;
            }
        }
        if (i == 0xffff) {
            pr_info("error: cannot find eocd\n");
            goto clean;
        }
    }

    pos += 12;
    ksu_kernel_read_compat(fp, &size4, 0x4, &pos);
    pos = size4 - 0x18;

    ksu_kernel_read_compat(fp, &size8, 0x8, &pos);
    ksu_kernel_read_compat(fp, buffer, 0x10, &pos);
    if (strcmp((char *)buffer, "APK Sig Block 42")) {
        goto clean;
    }

    pos = size4 - (size8 + 0x8);
    ksu_kernel_read_compat(fp, &size_of_block, 0x8, &pos);
    if (size_of_block != size8) {
        goto clean;
    }

    int loop_count = 0;
    while (loop_count++ < 10) {
        uint32_t id;
        uint32_t offset;
        ksu_kernel_read_compat(fp, &size8, 0x8, &pos); 
        if (size8 == size_of_block) {
            break;
        }
        ksu_kernel_read_compat(fp, &id, 0x4, &pos); 
        offset = 4;
        if (id == 0x7109871au) {
            v2_signing_blocks++;
            bool result = check_block(fp, &size4, &pos, &offset, &matched_index);
            if (result) {
                v2_signing_valid = true;
            }
        } else if (id == 0xf05368c0u) {
            v3_signing_exist = true;
        } else if (id == 0x1b93ad61u) {
            v3_1_signing_exist = true;
        } else {
#ifdef CONFIG_KSU_DEBUG
            pr_info("Unknown id: 0x%08x\n", id);
#endif
        }
        pos += (size8 - offset);
    }

    if (v2_signing_blocks != 1) {
#ifdef CONFIG_KSU_DEBUG
        pr_err("Unexpected v2 signature count: %d\n", v2_signing_blocks);
#endif
        v2_signing_valid = false;
    }

clean:
    filp_close(fp, 0);

    if (v3_signing_exist || v3_1_signing_exist) {
#ifdef CONFIG_KSU_DEBUG
        pr_err("Unexpected v3 signature scheme found!\n");
#endif
        return false;
    }

    if (v2_signing_valid) {
        if (signature_index) {
            *signature_index = matched_index;
        }

        return true;
    }
    return false;
}

#ifdef CONFIG_KSU_DEBUG
int ksu_debug_manager_appid = -1;

static int set_expected_size(const char *val, const struct kernel_param *kp)
{
    int rv = param_set_uint(val, kp);
    ksu_unregister_manager_by_signature_index(KSU_SIGNATURE_INDEX_KSU_DEBUG);
    ksu_register_manager(ksu_debug_manager_appid, KSU_SIGNATURE_INDEX_KSU_DEBUG);
    pr_info("ksu_manager_appid set to %d\n", ksu_debug_manager_appid);
    return rv;
}

static struct kernel_param_ops expected_size_ops = {
    .set = set_expected_size,
    .get = param_get_uint,
};

module_param_cb(ksu_debug_manager_appid, &expected_size_ops, &ksu_debug_manager_appid, S_IRUSR | S_IWUSR);

#endif

int get_pkg_from_apk_path(char *pkg, const char *path)
{
    int len = strlen(path);
    if (len >= KSU_MAX_PACKAGE_NAME || len < 1)
        return -1;

    const char *last_slash = NULL;
    const char *second_last_slash = NULL;

    int i;
    for (i = len - 1; i >= 0; i--) {
        if (path[i] == '/') {
            if (!last_slash) {
                last_slash = &path[i];
            } else {
                second_last_slash = &path[i];
                break;
            }
        }
    }

    if (!last_slash || !second_last_slash)
        return -1;

    const char *last_hyphen = strchr(second_last_slash, '-');
    if (!last_hyphen || last_hyphen > last_slash)
        return -1;

    int pkg_len = last_hyphen - second_last_slash - 1;
    if (pkg_len >= KSU_MAX_PACKAGE_NAME || pkg_len <= 0)
        return -1;

    strncpy(pkg, second_last_slash + 1, pkg_len);
    pkg[pkg_len] = '\0';

    return 0;
}

bool is_manager_apk(char *path, u8 *signature_index)
{
#ifdef KSU_MANAGER_PACKAGE
    char pkg[KSU_MAX_PACKAGE_NAME];
    if (get_pkg_from_apk_path(pkg, path) < 0) {
        pr_err("Failed to get package name from apk path: %s\n", path);
        return false;
    }

    if (strncmp(pkg, KSU_MANAGER_PACKAGE, sizeof(KSU_MANAGER_PACKAGE))) {
        return false;
    }
#endif
   {
    if (check_v2_signature(path, signature_index))
        return true;

    if (check_v1_signature(path, signature_index))
        return true;

    return false;
   }
}
