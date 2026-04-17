package com.resukisu.resukisu

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

/**
 * @author weishu
 * @date 2022/12/8.
 */
object Natives {
    // minimal supported kernel version
    // 10915: allowlist breaking change, add app profile
    // 10931: app profile struct add 'version' field
    // 10946: add capabilities
    // 10977: change groups_count and groups to avoid overflow write
    // 11071: Fix the issue of failing to set a custom SELinux type.
    // 12143: breaking: new supercall impl
    // 32310: new get_allow_list ioctl
    // 34634(upstream 32336): new set_sepolicy ioctl 
    // 34685(upstream 32377): add set_init_pgrp ioctl
    // 34709: breaking: unify uapi
    // 34713: change kernel_su_domain to u:r:ksu:s0
    // 34795: feature id 3 to adb root
    const val MINIMAL_SUPPORTED_KERNEL = 34795

    const val KERNEL_SU_DOMAIN = "u:r:ksu:s0"

    const val ROOT_UID = 0
    const val ROOT_GID = 0

    external fun getFullVersion(): String

    init {
        System.loadLibrary("kernelsu")
    }

    val version: Int
        external get

    val isSafeMode: Boolean
        external get

    val isLkmMode: Boolean
        external get

    val isLateLoadMode: Boolean
        external get
    val isManager: Boolean
        external get

    enum class KernelPatchImplement {
        /**
         * Kernel Patch was not found in this kernel
         */
        NO_KERNEL_PATCH_SUPPORT,

        /**
         * Detected Kernel Patch official in this kernel
         *
         * Manager should warn user it may conflict with KernelSU
         *
         * @see <a href="https://github.com/bmax121/KernelPatch">https://github.com/bmax121/KernelPatch</a>
         */
        KERNEL_PATCH_OFFICIAL,

        /**
         * Detected Rifsxd's Kernel Patch fork in this kernel
         *
         * Manager should warn user manager's built in kpm management will stop working
         *
         * @see <a href="https://github.com/KernelSU-Next/KPatch-Next">https://github.com/KernelSU-Next/KPatch-Next</a>
         */
        KPATCH_NEXT,

        /**
         * Detected SukiSU's Kernel Patch fork in this kernel
         *
         * Manager should warn user this feature are unstable and are not maintain for a long time
         *
         * @see <a href="https://github.com/SukiSU-Ultra/SukiSU_KernelPatch_patch">https://github.com/SukiSU-Ultra/SukiSU_KernelPatch_patch</a>
         */
        SUKISU_KERNEL_PATCH_PATCH
    }

    /**
     * Get Kernel Patch Implement
     * @return type
     * @throws IllegalStateException when can't access KernelPatchImplement enum
     */
    external fun getKernelPatchImplement(): KernelPatchImplement

    external fun uidShouldUmount(uid: Int): Boolean

    /**
     * Get the profile of the given package.
     * @param key usually the package name
     * @return return null if failed.
     */
    external fun getAppProfile(key: String?, uid: Int): Profile
    external fun setAppProfile(profile: Profile?): Boolean

    /**
     * `su` compat mode can be disabled temporarily.
     *  0: disabled
     *  1: enabled
     *  negative : error
     */
    external fun isSuEnabled(): Boolean
    external fun setSuEnabled(enabled: Boolean): Boolean

    external fun isSuLogEnabled(): Boolean
    external fun setSuLogEnabled(enabled: Boolean): Boolean

    /**
     * Kernel module umount can be disabled temporarily.
     *  0: disabled
     *  1: enabled
     *  negative : error
     */
    external fun isKernelUmountEnabled(): Boolean
    external fun setKernelUmountEnabled(enabled: Boolean): Boolean

    external fun isKPMEnabled(): Boolean
    external fun getHookType(): String

    /**
     * Set dynamic managerature configuration
     * @param size APK signature size
     * @param hash APK signature hash (64 character hex string)
     * @return true if successful, false otherwise
     */
    external fun setDynamicManager(size: Int, hash: String): Boolean


    /**
     * Get current dynamic manager configuration
     * @return DynamicManagerConfig object containing current configuration, or null if not set
     */
    external fun getDynamicManager(): DynamicManagerConfig?

    /**
     * Clear dynamic manager configuration
     * @return true if successful, false otherwise
     */
    external fun clearDynamicManager(): Boolean

    /**
     * Get active managers list
     * @return ManagersList object containing active managers, or null if failed or not enabled
     */
    external fun getManagersList(): ManagersList?

    external fun getUserName(uid: Int): String?

    external fun getSuperuserCount(): Int

    private const val NON_ROOT_DEFAULT_PROFILE_KEY = "$"
    private const val NOBODY_UID = 9999

    fun setDefaultUmountModules(umountModules: Boolean): Boolean {
        Profile(
            NON_ROOT_DEFAULT_PROFILE_KEY,
            NOBODY_UID,
            false,
            umountModules = umountModules
        ).let {
            return setAppProfile(it)
        }
    }

    fun isDefaultUmountModules(): Boolean {
        getAppProfile(NON_ROOT_DEFAULT_PROFILE_KEY, NOBODY_UID).let {
            return it.umountModules
        }
    }

    fun requireNewKernel(): Boolean {
        return version != -1 && version < MINIMAL_SUPPORTED_KERNEL
    }

    @Immutable
    @Parcelize
    @Keep
    data class DynamicManagerConfig(
        val size: Int = 0,
        val hash: String = ""
    ) : Parcelable {

        fun isValid(): Boolean {
            return size > 0 && hash.length == 64 && hash.all {
                it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
            }
        }
    }

    @Immutable
    @Parcelize
    @Keep
    data class ManagersList(
        val count: Int = 0,
        val managers: List<ManagerInfo> = emptyList()
    ) : Parcelable

    @Immutable
    @Parcelize
    @Keep
    data class ManagerInfo(
        val uid: Int = 0,
        val signatureIndex: Int = 0
    ) : Parcelable

    @Immutable
    @Parcelize
    @Keep
    data class Profile(
        // and there is a default profile for root and non-root
        val name: String,
        // current uid for the package, this is convivent for kernel to check
        // if the package name doesn't match uid, then it should be invalidated.
        val currentUid: Int = 0,

        // if this is true, kernel will grant root permission to this package
        val allowSu: Boolean = false,

        // these are used for root profile
        val rootUseDefault: Boolean = true,
        val rootTemplate: String? = null,
        val uid: Int = ROOT_UID,
        val gid: Int = ROOT_GID,
        val groups: List<Int> = mutableListOf(),
        val capabilities: List<Int> = mutableListOf(),
        val context: String = KERNEL_SU_DOMAIN,
        val namespace: Int = Namespace.INHERITED.ordinal,

        val nonRootUseDefault: Boolean = true,
        val umountModules: Boolean = true,
        var rules: String = "", // this field is save in ksud!!
    ) : Parcelable {
        enum class Namespace {
            INHERITED,
            GLOBAL,
            INDIVIDUAL,
        }

        constructor() : this("")
    }
}
