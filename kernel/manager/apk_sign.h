#ifndef __KSU_H_APK_V2_SIGN
#define __KSU_H_APK_V2_SIGN

#include <linux/types.h>
#include "ksu.h"

bool is_manager_apk(char *path, u8 *signature_index);
int get_pkg_from_apk_path(char *pkg, const char *path);

bool is_dynamic_manager_apk(char *path, int *signature_index);

#endif