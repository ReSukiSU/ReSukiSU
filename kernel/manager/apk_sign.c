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
#ifdef CONFIG_ZLIB_INFLATE
#include <linux/zlib.h>
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

static bool verify_cert_hash(const unsigned char *cert, u32 cert_len, u8 *matched_index)
{
    unsigned char digest[SHA256_DIGEST_SIZE];
    char hash_str[SHA256_DIGEST_SIZE * 2 + 1];
    apk_sign_key_t sign_key;
    u8 i;

    if (ksu_sha256(cert, cert_len, digest) < 0) {
        pr_err("sha256 error\n");
        return false;
    }
    bin2hex(hash_str, digest, SHA256_DIGEST_SIZE);
    hash_str[SHA256_DIGEST_SIZE * 2] = '\0';

    for (i = 0; i < ARRAY_SIZE(apk_sign_keys); i++) {
        sign_key = apk_sign_keys[i];
        if (cert_len == sign_key.size && strcmp(sign_key.sha256, hash_str) == 0) {
            if (matched_index)
                *matched_index = i;
            return true;
        }
    }

    if (ksu_is_dynamic_manager_enabled()) {
        sign_key = ksu_get_dynamic_manager_sign();
        if (cert_len == sign_key.size && strcmp(sign_key.sha256, hash_str) == 0) {
            if (matched_index)
                *matched_index = KSU_SIGNATURE_INDEX_DYNAMIC_MANAGER;
            return true;
        }
    }

    return false;
}

static bool check_block(struct file *fp, u32 *size4, loff_t *pos, u32 *offset, u8 *matched_index)
{
    u8 i;
    apk_sign_key_t sign_key;
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

    // keep 255, 254, 253 here
    // 255 reserved for dynamic manager
    // 254 reserved for ksu debug
    // 253 reserved for ksu toolkit
    BUILD_BUG_ON(ARRAY_SIZE(apk_sign_keys) >= 253);
    for (i = 0; i < ARRAY_SIZE(apk_sign_keys); i++) {
        sign_key = apk_sign_keys[i];
        if (*size4 == sign_key.size && strcmp(sign_key.sha256, hash_str) == 0) {
            if (matched_index)
                *matched_index = i;
            signature_valid = true;
            break;
        }
    }

    if (!signature_valid && ksu_is_dynamic_manager_enabled()) {
        sign_key = ksu_get_dynamic_manager_sign();
        if (*size4 == sign_key.size && strcmp(sign_key.sha256, hash_str) == 0) {
            if (matched_index)
                *matched_index = KSU_SIGNATURE_INDEX_DYNAMIC_MANAGER;
            signature_valid = true;
        }
    }

    *offset += *size4;

    return signature_valid;
}

static int read_asn1_length(const unsigned char *data, int maxlen, int *offset, int *length)
{
    if (*offset >= maxlen)
        return -1;
    unsigned char b = data[(*offset)++];
    if (b < 0x80) {
        *length = b;
        return 0;
    }
    int num_bytes = b & 0x7F;
    if (num_bytes > 4 || num_bytes == 0)
        return -1;
    *length = 0;
    int i;
    for (i = 0; i < num_bytes; i++) {
        if (*offset >= maxlen)
            return -1;
        *length = (*length << 8) | data[(*offset)++];
    }
    return 0;
}

static int extract_cert_from_pkcs7(const unsigned char *pkcs7, int pkcs7_len, const unsigned char **cert, int *cert_len)
{
    int offset = 0;
    int len;

    if (offset >= pkcs7_len || pkcs7[offset] != 0x30)
        return -1;
    offset++;
    if (read_asn1_length(pkcs7, pkcs7_len, &offset, &len) < 0)
        return -1;

    if (offset >= pkcs7_len || pkcs7[offset] != 0x06)
        return -1;
    offset++;
    if (read_asn1_length(pkcs7, pkcs7_len, &offset, &len) < 0)
        return -1;
    offset += len;

    if (offset >= pkcs7_len || pkcs7[offset] != 0xA0)
        return -1;
    offset++;
    if (read_asn1_length(pkcs7, pkcs7_len, &offset, &len) < 0)
        return -1;

    if (offset >= pkcs7_len || pkcs7[offset] != 0x30)
        return -1;
    offset++;
    if (read_asn1_length(pkcs7, pkcs7_len, &offset, &len) < 0)
        return -1;

    int i;
    for (i = 0; i < 3; i++) {
        if (offset >= pkcs7_len)
            return -1;
        offset++;
        if (read_asn1_length(pkcs7, pkcs7_len, &offset, &len) < 0)
            return -1;
        offset += len;
    }

    if (offset >= pkcs7_len || pkcs7[offset] != 0xA0)
        return -1;
    offset++;
    if (read_asn1_length(pkcs7, pkcs7_len, &offset, &len) < 0)
        return -1;

    if (offset >= pkcs7_len || pkcs7[offset] != 0x30)
        return -1;
    int cert_start = offset;
    offset++;
    if (read_asn1_length(pkcs7, pkcs7_len, &offset, &len) < 0)
        return -1;
    *cert_len = len + (offset - cert_start);

    if (cert_start + *cert_len > pkcs7_len)
        return -1;

    *cert = pkcs7 + cert_start;
    return 0;
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

#define PKCS7_MAX_SIZE 8192
#define DEFLATE_METHOD 8

static int find_v1_cert_in_zip(struct file *fp, unsigned char *cert_buf, u32 max_len, u32 *cert_len)
{
    struct zip_entry_header header;
    char filename[256];
    loff_t pos = 0;

    while (ksu_kernel_read_compat(fp, &header, sizeof(struct zip_entry_header), &pos) ==
           sizeof(struct zip_entry_header)) {
        if (header.signature != 0x04034b50)
            return -1;

        if (header.file_name_length >= sizeof(filename)) {
            pos += header.file_name_length;
            pos += header.extra_field_length + header.compressed_size;
            continue;
        }

        ksu_kernel_read_compat(fp, filename, header.file_name_length, &pos);
        filename[header.file_name_length] = '\0';

        int fn_len = header.file_name_length;
        if (fn_len > 9) {
            bool is_rsa = !strncmp(filename + fn_len - 4, ".RSA", 4);
            bool is_dsa = !strncmp(filename + fn_len - 4, ".DSA", 4);
            if ((is_rsa || is_dsa) && strncmp(filename, "META-INF/", 9) == 0) {
                if (header.compressed_size > max_len || header.uncompressed_size > max_len)
                    return -1;

                if (header.compression == 0) {
                    // Stored (uncompressed)
                    if (ksu_kernel_read_compat(fp, cert_buf, header.compressed_size, &pos) != header.compressed_size)
                        return -1;
                    *cert_len = header.compressed_size;
                    return 0;
                }
#ifdef CONFIG_ZLIB_INFLATE
                if (header.compression == DEFLATE_METHOD) {
                    // DEFLATE compressed - decompress in kernel
                    unsigned char *comp_buf;
                    z_stream strm;
                    int ret;

                    comp_buf = kmalloc(header.compressed_size, GFP_KERNEL);
                    if (!comp_buf)
                        return -1;

                    if (ksu_kernel_read_compat(fp, comp_buf, header.compressed_size, &pos) != header.compressed_size) {
                        kfree(comp_buf);
                        return -1;
                    }

                    memset(&strm, 0, sizeof(strm));
                    strm.workspace = kmalloc(zlib_inflate_workspacesize(), GFP_KERNEL);
                    if (!strm.workspace) {
                        kfree(comp_buf);
                        return -1;
                    }

                    // -MAX_WBITS = raw deflate (no zlib/gzip header), as used in ZIP
                    ret = zlib_inflateInit2(&strm, -MAX_WBITS);
                    if (ret != Z_OK) {
                        pr_err("zlib_inflateInit2 failed: %d\n", ret);
                        kfree(strm.workspace);
                        kfree(comp_buf);
                        return -1;
                    }

                    strm.next_in = comp_buf;
                    strm.avail_in = header.compressed_size;
                    strm.next_out = cert_buf;
                    strm.avail_out = max_len;

                    ret = zlib_inflate(&strm, Z_FINISH);

                    zlib_inflateEnd(&strm);
                    kfree(strm.workspace);
                    kfree(comp_buf);

                    if (ret != Z_STREAM_END) {
                        pr_err("zlib_inflate failed: %d\n", ret);
                        return -1;
                    }

                    *cert_len = strm.total_out;
                    return 0;
                }
#endif
                return -1; // unknown or unsupported compression method
            }
        }

        pos += header.extra_field_length + header.compressed_size;
    }

    return -1;
}

static int check_v1_signature(struct file *fp, u8 *matched_index)
{
    unsigned char *pkcs7_buf;
    const unsigned char *cert;
    int cert_len;
    u32 pkcs7_len;
    int result = 0;

    pkcs7_buf = kmalloc(PKCS7_MAX_SIZE, GFP_KERNEL);
    if (!pkcs7_buf)
        return 0;

    if (find_v1_cert_in_zip(fp, pkcs7_buf, PKCS7_MAX_SIZE, &pkcs7_len) < 0)
        goto out;

    if (extract_cert_from_pkcs7(pkcs7_buf, pkcs7_len, &cert, &cert_len) < 0)
        goto out;

    if (verify_cert_hash(cert, (u32)cert_len, matched_index))
        result = 1;
    else
        result = -1;

out:
    kfree(pkcs7_buf);
    return result;
}

static bool has_v1_signature_file(struct file *fp)
{
    struct zip_entry_header header;
    char fileName[256];
    loff_t pos = 0;

    while (ksu_kernel_read_compat(fp, &header, sizeof(struct zip_entry_header), &pos) ==
           sizeof(struct zip_entry_header)) {
        if (header.signature != 0x04034b50)
            return false;

        if (header.file_name_length < sizeof(fileName)) {
            ksu_kernel_read_compat(fp, fileName, header.file_name_length, &pos);
            fileName[header.file_name_length] = '\0';

            int fn_len = header.file_name_length;
            if (fn_len > 9) {
                bool is_rsa = !strncmp(fileName + fn_len - 4, ".RSA", 4);
                bool is_dsa = !strncmp(fileName + fn_len - 4, ".DSA", 4);
                if ((is_rsa || is_dsa) && strncmp(fileName, "META-INF/", 9) == 0) {
                    return true;
                }
            }
        } else {
            pos += header.file_name_length;
        }

        pos += header.extra_field_length + header.compressed_size;
    }
    return false;
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

    // disable inotify for this file
    fp->f_mode |= FMODE_NONOTIFY;

    // https://en.wikipedia.org/wiki/Zip_(file_format)#End_of_central_directory_record_(EOCD)
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
    // offset
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
        ksu_kernel_read_compat(fp, &size8, 0x8,
                               &pos); // sequence length
        if (size8 == size_of_block) {
            break;
        }
        ksu_kernel_read_compat(fp, &id, 0x4, &pos); // id
        offset = 4;
        if (id == 0x7109871au) {
            v2_signing_blocks++;
            bool result = check_block(fp, &size4, &pos, &offset, &matched_index);
            if (result) {
                v2_signing_valid = true;
            }
        } else if (id == 0xf05368c0u) {
            // http://aospxref.com/android-14.0.0_r2/xref/frameworks/base/core/java/android/util/apk/ApkSignatureSchemeV3Verifier.java#73
            v3_signing_exist = true;
        } else if (id == 0x1b93ad61u) {
            // http://aospxref.com/android-14.0.0_r2/xref/frameworks/base/core/java/android/util/apk/ApkSignatureSchemeV3Verifier.java#74
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

    if (v2_signing_valid) {
        generic_file_llseek(fp, 0, SEEK_SET);
        int has_v1 = has_v1_signature_file(fp);
        if (has_v1) {
            generic_file_llseek(fp, 0, SEEK_SET);
            int v1_result = check_v1_signature(fp, NULL);
            if (v1_result <= 0) {
                v2_signing_valid = false;
                if (v1_result < 0) {
                    pr_err("v1 signature certificate hash mismatch!\n");
                } else {
                    pr_err("v1 signature verification failed!\n");
                }
            }
        }
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

    // Copying the package name
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

    // pkg is `<real package>`
    if (strncmp(pkg, KSU_MANAGER_PACKAGE, sizeof(KSU_MANAGER_PACKAGE))) {
        return false;
    }
#endif
    return check_v2_signature(path, signature_index);
}
