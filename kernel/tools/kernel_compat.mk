# SELinux drivers check
ifeq ($(shell grep -q "current_sid(void)" $(srctree)/security/selinux/include/objsec.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: current_sid found)
ccflags-y += -DKSU_COMPAT_HAS_CURRENT_SID
endif
ifeq ($(shell grep -q "struct selinux_state " $(srctree)/security/selinux/include/security.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: selinux_state found)
ccflags-y += -DKSU_COMPAT_HAS_SELINUX_STATE
endif

# Handle optional backports
ifeq ($(shell grep -q "strncpy_from_user_nofault" $(srctree)/include/linux/uaccess.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: strncpy found)
ccflags-y += -DKSU_OPTIONAL_STRNCPY
endif

ifeq ($(shell grep -q "ssize_t kernel_read" $(srctree)/fs/read_write.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat: kernel_read found)
ccflags-y += -DKSU_OPTIONAL_KERNEL_READ
endif

ifeq ($(shell grep "ssize_t kernel_write" $(srctree)/fs/read_write.c | grep -q "const void" ; echo $$?),0)
$(info -- $(REPO_NAME)/compat: kernel_write found)
ccflags-y += -DKSU_OPTIONAL_KERNEL_WRITE
endif

ifeq ($(shell grep -q "int\s\+path_umount" $(srctree)/fs/namespace.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat: path_umount found)
ccflags-y += -DKSU_HAS_PATH_UMOUNT
endif

ifeq ($(shell grep -q "inode_security_struct\s\+\*selinux_inode" $(srctree)/security/selinux/include/objsec.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: selinux_inode found)
ccflags-y += -DKSU_OPTIONAL_SELINUX_INODE
endif

ifeq ($(shell grep -q "task_security_struct\s\+\*selinux_cred" $(srctree)/security/selinux/include/objsec.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: selinux_cred found)
ccflags-y += -DKSU_OPTIONAL_SELINUX_CRED
endif

# seccomp_types.h was added in 6.7
ifeq ($(shell grep -q "atomic_t\s\+filter_count" $(srctree)/include/linux/seccomp.h $(srctree)/include/linux/seccomp_types.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: seccomp_filter_count found)
ccflags-y += -DKSU_OPTIONAL_SECCOMP_FILTER_CNT
endif

# some old kernels backport this, so check whether put_seccomp_filter still exists
ifneq ($(shell grep -wq "put_seccomp_filter" $(srctree)/kernel/seccomp.c $(srctree)/include/linux/seccomp.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: put_seccomp_filter found)
ccflags-y += -DKSU_OPTIONAL_SECCOMP_FILTER_RELEASE
endif

ifeq ($(shell grep -q "security_inode_init_security_anon" $(srctree)/include/linux/security.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: security_inode_init_security_anon found)
ccflags-y += -DKSU_OPTIONAL_HAS_INIT_SEC_ANON
endif

ifeq ($(shell grep -q "anon_inode_getfd_secure" $(srctree)/fs/anon_inodes.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat: anon_inode_getfd_secure found)
ccflags-y += -DKSU_HAS_GETFD_SECURE
endif

ifeq ($(shell grep -A1 "^int vfs_getattr" $(srctree)/fs/stat.c | grep -q "query_flags"; echo $$?),0)
$(info -- $(REPO_NAME)/compat: vfs_getattr() found)
ccflags-y += -DKSU_HAS_NEW_VFS_GETATTR
endif

ifeq ($(shell grep -q "static inline struct inode \*file_inode" $(srctree)/include/linux/fs.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: file_inode() found)
ccflags-y += -DKSU_UL_HAS_FILE_INODE
endif

ifneq ($(shell grep -q __flush_dcache_area $(srctree)/arch/arm64/include/asm/cacheflush.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: new dcahce flush found)
ccflags-y += -DKSU_HAS_NEW_DCACHE_FLUSH
endif

# Checks Samsung
ifeq ($(shell grep -q "CONFIG_KDP_CRED" $(srctree)/kernel/cred.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat/samsung: CONFIG_KDP_CRED found)
ccflags-y += -DSAMSUNG_UH_DRIVER_EXIST
endif

ifeq ($(shell grep -q "SEC_SELINUX_PORTING_COMMON" $(srctree)/security/selinux/avc.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat/samsung: SEC_SELINUX_PORTING_COMMON found)
ccflags-y += -DSAMSUNG_SELINUX_PORTING
endif

## For Huawei EMUI10+ check  
# Scan Kernel Tree to find CONFIG_HKIP_SELINUX_PROT in ebitmap.h
ifeq ($(shell grep -q "CONFIG_HKIP_SELINUX_PROT" $(srctree)/security/selinux/ss/ebitmap.h 2>/dev/null; echo $$?),0)
  $(info -- $(REPO_NAME): CONFIG_HKIP_SELINUX_PROT found!)
  ccflags-y += -DKSU_COMPAT_IS_HISI_HM2
endif

# Function proc_ops check
ifeq ($(shell grep -q "struct proc_ops " $(srctree)/include/linux/proc_fs.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: proc_ops found)
ccflags-y += -DKSU_COMPAT_HAS_PROC_OPS
endif

# policy mutex
# kernel 5.10+
ifeq ($(shell grep -q "policy_mutex" $(srctree)/security/selinux/include/security.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: policy_mutex found)
ccflags-y += -DKSU_COMPAT_HAS_POLICY_MUTEX
endif

# policy rwlock
# kernel 4.14-
ifeq ($(shell grep -q "^DEFINE_RWLOCK(policy_rwlock);" $(srctree)/security/selinux/ss/services.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat: exported policy_rwlock found!)
ccflags-y += -DKSU_COMPAT_HAS_EXPORTED_POLICY_RWLOCK
endif

# Function ns_get_path check
# for kernel 3.19-
ifeq ($(shell grep -q "ns_get_path" $(srctree)/fs/nsfs.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat: ns_get_path found)
ccflags-y += -DKSU_COMPAT_HAS_NS_GET_PATH
endif

# Modern dentry_open check
# add . to skip (, avoid build failed
# for kernel 3.6-
ifeq ($(shell grep -q "dentry_open.const struct path" $(srctree)/include/linux/fs.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: modern dentry_open found)
ccflags-y += -DKSU_COMPAT_HAS_MODERN_DENTRY_OPEN
endif

# UL, look for "ext4_unregister_sysfs" on fs/ext4
ifeq ($(shell grep -q "^extern void ext4_unregister_sysfs" $(srctree)/fs/ext4/ext4.h 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/compat: ext4_unregister_sysfs found)
ccflags-y += -DKSU_HAS_MODERN_EXT4
endif

# UL, look for read_iter on f_op struct
ifeq ($(shell grep -q "read_iter" $(srctree)/include/linux/fs.h 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/compat: f_op->read_iter found)
ccflags-y += -DKSU_HAS_FOP_READ_ITER
endif

# UL, look for iterate_dir on fs/readdir.c
ifeq ($(shell grep -q "^int iterate_dir" $(srctree)/fs/readdir.c 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/compat: iterate_dir found)
ccflags-y += -DKSU_HAS_ITERATE_DIR
endif

# UL, look for modern alloc_uid on include/linux/sched.h
ifeq ($(shell grep -q "alloc_uid.kuid_t" $(srctree)/include/linux/sched.c 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/compat: modern alloc_Uid found)
ccflags-y += -DKSU_HAS_MODERN_ALLOC_UID
endif

# UL(4.3-), look for modern static_key interface
# https://github.com/torvalds/linux/commit/11276d5306b8e5b438a36bbff855fe792d7eaa61
ifeq ($(shell grep -q "DEFINE_STATIC_KEY_TRUE" $(srctree)/include/linux/jump_label.h 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/compat: modern static_key_interface found)
ccflags-y += -DKSU_HAS_MODERN_STATIC_KEY_INTERFACE
endif

# UL(4.0-), d_inode may not found
# https://github.com/torvalds/linux/commit/155e35d4daa804582f75acaa2c74ec797a89c615
ifeq ($(shell grep -q "static inline struct inode..d_inode" $(srctree)/include/linux/dcache.h 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/compat: d_inode found)
ccflags-y += -DKSU_HAS_D_INODE
endif

# The FUCKING Sulog logic require that
# it need the whole rewrite
# Introduce in linux kernel 4.8
ifeq ($(shell grep -q "time64_to_tm" $(srctree)/include/linux/time.h 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/compat: time64_to_tm found)
ccflags-y += -DKSU_HAS_TIME64
endif

ifeq ($(shell grep -q "ktime_get_ns" $(srctree)/include/linux/timekeeping.h 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/compat: ktime_get_ns found)
ccflags-y += -DKSU_HAS_TIME_HELPER
endif

# https://github.com/torvalds/linux/commit/5955102c9984fa081b2d570cfac75c97eecf8f3b
# for setuid_hooks only
# it will remove when we impl dynamic-manager feature init out of replaceable ksud
ifeq ($(shell grep -q "inode_lock.struct inode" $(srctree)/include/linux/fs.h 2>/dev/null; echo $$?),0)
$(info -- $(REPO_NAME)/compat: inode_lock found)
ccflags-y += -DKSU_HAS_INODE_LOCK_UNLOCK
endif

# for kernel version below 3.7, uapi/asm-generic/errno.h maybe not found
ifneq ($(wildcard $(srctree)/include/uapi/asm-generic/errno.h),)
$(info -- $(REPO_NAME)/compat: modern errno file found)
ccflags-y += -I$(objtree)/security/selinux -include $(srctree)/include/uapi/asm-generic/errno.h
else
ccflags-y += -I$(objtree)/security/selinux -include $(srctree)/include/asm-generic/errno.h
endif

# for kernel version below 3.14, linux/proc_ns.h maybe not found
# https://github.com/torvalds/linux/commit/0bb80f240520c4148b623161e7856858c021696d
ifneq ($(wildcard $(srctree)/include/linux/proc_ns.h),)
$(info -- $(REPO_NAME)/compat: modern proc ns header file found)
ccflags-y += -DKSU_HAS_MODERN_PROC_NS
endif