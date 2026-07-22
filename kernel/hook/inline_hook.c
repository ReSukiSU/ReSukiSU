#include "hook/inline_hook_internal.h"

#include <linux/errno.h>
#include <linux/err.h>
#include <linux/kernel.h>
#include <linux/slab.h>
#include <linux/string.h>

#include "feature/sucompat.h"
#include "hook/patch_memory.h"
#include "klog.h"

void *ksu_inline_hook_before(struct ksu_inline_hook *hook, unsigned long *arg_regs)
{
    struct pt_regs regs;

    if (!hook)
        return NULL;

    if (!hook->before)
        goto out;

    ksu_inline_hook_arch_setup_regs(&regs, arg_regs);

    if (!READ_ONCE(hook->unregistering)) {
        hook->before(&regs);
        ksu_inline_hook_arch_update_args(&regs, arg_regs);
    }

out:
    return hook->clone ?: (void *)((unsigned long)hook->target + hook->patch_size);
}

unsigned long ksu_inline_hook_after(struct ksu_inline_hook *hook, unsigned long ret, unsigned long *arg_regs)
{
    struct pt_regs regs;

    if (!hook)
        return ret;

    if (!hook->after)
        return ret;

    ksu_inline_hook_arch_setup_regs(&regs, arg_regs);
    ksu_inline_hook_arch_set_ret(&regs, ret);

    if (!READ_ONCE(hook->unregistering)) {
        hook->after(&regs);
        ret = ksu_inline_hook_arch_get_ret(&regs);
    }

    return ret;
}

unsigned long ksu_inline_hook_entry_dispatch(struct ksu_inline_hook *hook, unsigned long arg0, unsigned long arg1,
                                             unsigned long arg2, unsigned long arg3, unsigned long arg4,
                                             unsigned long arg5, unsigned long arg6)
{
    typedef unsigned long (*ksu_inline_clone_fn_t)(unsigned long, unsigned long, unsigned long, unsigned long,
                                                   unsigned long, unsigned long, unsigned long);
    unsigned long args[] = { arg0, arg1, arg2, arg3, arg4, arg5, arg6, 0, 0 };
    ksu_inline_clone_fn_t clone;
    unsigned long ret;

    if (!hook)
        return 0;

    clone = (ksu_inline_clone_fn_t)(hook->clone ?: (void *)((unsigned long)hook->target + hook->patch_size));

    if (hook->before)
        clone = (ksu_inline_clone_fn_t)ksu_inline_hook_before(hook, args);

    ret = clone(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);

    if (hook->after)
        ret = ksu_inline_hook_after(hook, ret, args);

    return ret;
}

struct ksu_inline_hook *ksu_inline_hook_register(const struct ksu_inline_hook_config config)
{
    struct ksu_inline_hook *hook;
    u8 patch[KSU_INLINE_MAX_PATCH_SIZE];
    void *target;
    size_t patch_size;
    int ret;

    if (!config.target || (!config.before && !config.after))
        return ERR_PTR(-EINVAL);

    patch_size = ksu_inline_hook_arch_patch_size();
    if (!patch_size || patch_size > sizeof(patch))
        return ERR_PTR(-EOPNOTSUPP);

    hook = kzalloc(sizeof(*hook), GFP_KERNEL);
    if (!hook)
        return ERR_PTR(-ENOMEM);

    target = ksu_inline_hook_arch_normalize_target(config.target);
    hook->target = target;
    hook->before = config.before;
    hook->after = config.after;
    hook->patch_size = patch_size;
    hook->slot = KSU_INLINE_INVALID_SLOT;
    memcpy(hook->orig, target, hook->patch_size);

    ret = ksu_inline_hook_arch_prepare(hook, patch, patch_size);
    if (ret)
        goto err_free;

    ret = ksu_patch_text(target, patch, patch_size, KSU_PATCH_TEXT_FLUSH_ICACHE);
    if (ret)
        goto err_release;

    hook->active = true;
    pr_info("inline_hook: hooked target=%px before=%px after=%px\n", config.target, config.before, config.after);
    return hook;

err_release:
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
