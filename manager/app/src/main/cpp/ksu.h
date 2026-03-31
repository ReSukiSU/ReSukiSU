//
// Created by weishu on 2022/12/9.
//

#ifndef KERNELSU_KSU_H
#define KERNELSU_KSU_H

#include "prelude.h"
#include <stdint.h>
#include <sys/types.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <sys/syscall.h>

#include "uapi/supercall.h"

uint32_t get_version();

bool uid_should_umount(int uid);

bool is_safe_mode();

bool is_lkm_mode();

bool is_manager();
bool is_late_load_mode();

void get_full_version(char* buff);

bool set_app_profile(const struct app_profile *profile);

int get_app_profile(struct app_profile* profile);

bool is_KPM_enable();

void get_hook_type(char* hook_type);

bool set_dynamic_manager(unsigned int size, const char* hash);

bool get_dynamic_manager(struct ksu_dynamic_manager_cmd* config);

bool clear_dynamic_manager();

// Su compat
bool set_su_enabled(bool enabled);
bool is_su_enabled();

// Kernel umount
bool set_kernel_umount_enabled(bool enabled);
bool is_kernel_umount_enabled();

// Sulog
bool set_sulog_enabled(bool enabled);
bool is_sulog_enabled();

bool get_managers_list(struct ksu_get_managers_cmd **out_cmd);
bool get_allow_list(struct ksu_new_get_allow_list_cmd *);

// Legacy Compatible
struct ksu_version_info legacy_get_info();

struct ksu_version_info {
    int32_t version;
    int32_t flags;
};

bool legacy_get_allow_list(int *uids, int *size);
bool legacy_is_safe_mode();
bool legacy_uid_should_umount(int uid);
bool legacy_set_app_profile(const struct app_profile* profile);
bool legacy_get_app_profile(char* key, struct app_profile* profile);
bool legacy_set_su_enabled(bool enabled);
bool legacy_is_su_enabled();
bool legacy_is_KPM_enable();
bool legacy_get_hook_type(char* hook_type, size_t size);
void legacy_get_full_version(char* buff);

#endif //KERNELSU_KSU_H