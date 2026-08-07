#include "hook/inline_hook_internal.h"

#include <linux/errno.h>
#include <linux/err.h>
#include <linux/kernel.h>
#include <linux/slab.h>
#include <linux/string.h>

#include "feature/sucompat.h"
#include "hook/patch_memory.h"
#include "klog.h"

struct ksu_inline_hook *ksu_inline_hook_register(const struct ksu_inline_hook_config config)
{
    struct ksu_inline_hook *hook;
    u8 patch[KSU_INLINE_MAX_PATCH_SIZE];
    void *target;
    size_t patch_size;
    int ret;

    if (!config.target || !config.dispatcher || config.abi > KSU_INLINE_HOOK_ABI_ARM64_SYSCALL)
        return ERR_PTR(-EINVAL);

#ifndef __aarch64__
    if (config.abi == KSU_INLINE_HOOK_ABI_ARM64_SYSCALL)
        return ERR_PTR(-EOPNOTSUPP);
#endif

    target = ksu_inline_hook_arch_normalize_target(config.target);
    if (!kernel_text_address((unsigned long)target) || !kernel_text_address((unsigned long)config.dispatcher)) {
        pr_err("inline_hook: reject non-text target=%px dispatcher=%px\n", target, config.dispatcher);
        return ERR_PTR(-EINVAL);
    }

    patch_size = ksu_inline_hook_arch_patch_size(target);
    if (!patch_size || patch_size > sizeof(patch))
        return ERR_PTR(-EOPNOTSUPP);

    hook = kzalloc(sizeof(*hook), GFP_KERNEL);
    if (!hook)
        return ERR_PTR(-ENOMEM);

    hook->target = target;
    hook->dispatcher = config.dispatcher;
    hook->abi = config.abi;
    hook->patch_size = patch_size;
    hook->slot = KSU_INLINE_INVALID_SLOT;
    memcpy(hook->orig, target, hook->patch_size);

    ret = ksu_inline_hook_arch_prepare(hook, patch, patch_size);
    if (ret)
        goto err_free;

    if (config.owner)
        WRITE_ONCE(*config.owner, hook);

    ret = ksu_patch_text(target, patch, patch_size, KSU_PATCH_TEXT_FLUSH_ICACHE);
    if (ret)
        goto err_release;

    hook->active = true;
    pr_info("inline_hook: hooked target=%px dispatcher=%px\n", config.target, config.dispatcher);
    return hook;

err_release:
    if (config.owner && READ_ONCE(*config.owner) == hook)
        WRITE_ONCE(*config.owner, NULL);
    ksu_inline_hook_arch_release(hook);
err_free:
    kfree(hook);
    return ERR_PTR(ret);
}

void ksu_inline_hook_unregister(struct ksu_inline_hook *hook)
{
    int ret;

    if (!hook || !hook->active)
        return;

    WRITE_ONCE(hook->unregistering, true);

    ret = ksu_patch_text(hook->target, hook->orig, hook->patch_size, KSU_PATCH_TEXT_FLUSH_ICACHE);
    if (ret)
        pr_err("inline_hook: failed to restore target=%px: %d\n", hook->target, ret);

    ksu_inline_hook_arch_release(hook);
    hook->active = false;
    if (!hook->keep_storage)
        kfree(hook);
}
