#include <linux/slab.h>
#include <linux/rculist.h>
#include <linux/uaccess.h>
#include <linux/cred.h>
#include <linux/sched.h>
#include "supercalls.h"
#include "manager.h"
#include "ksu.h"
#include "kernel_compat.h"

u16 ksu_last_manager_appid = KSU_INVALID_APPID;

struct ksu_manager_node {
    u8 signature_index;
    u16 appid;
    struct list_head list;
    struct rcu_head rcu;
};

static LIST_HEAD(ksu_manager_appid_list);
static DEFINE_SPINLOCK(ksu_manager_list_write_lock);

bool ksu_is_manager_appid(u16 appid)
{
    bool found = false;
    struct ksu_manager_node *pos;

    rcu_read_lock();
    list_for_each_entry_rcu (pos, &ksu_manager_appid_list, list) {
        if (pos->appid == appid) {
            found = true;
            break;
        }
    }
    rcu_read_unlock();

    return found;
}

bool ksu_is_manager_uid(u32 uid)
{
    u16 appid = uid % PER_USER_RANGE;

    return ksu_is_manager_appid(appid);
}

bool is_manager(void)
{
    return ksu_is_manager_uid(ksu_get_uid_t(current_uid()));
}

void ksu_register_manager(u32 uid, u8 signature_index)
{
    struct ksu_manager_node *node;
    u16 appid;

    if (ksu_is_manager_uid(uid))
        return;

    node = kzalloc(sizeof(*node), GFP_ATOMIC);
    if (unlikely(!node))
        return;

    appid = uid % PER_USER_RANGE;

    node->appid = appid;
    node->signature_index = signature_index;

    spin_lock(&ksu_manager_list_write_lock);

    if (ksu_is_manager_uid(uid)) {
        spin_unlock(&ksu_manager_list_write_lock);
        kfree(node);
        return;
    }

    list_add_tail_rcu(&node->list, &ksu_manager_appid_list);

    spin_unlock(&ksu_manager_list_write_lock);

    if (ksu_last_manager_appid == KSU_INVALID_APPID)
        ksu_last_manager_appid = appid;
    return;
}

void ksu_unregister_manager(u32 uid)
{
    struct ksu_manager_node *node, *pos, *tmp;
    bool mark_another_manager = false;

    if (!ksu_is_manager_uid(uid))
        return;

    u16 appid = uid % PER_USER_RANGE;

    if (ksu_last_manager_appid == appid)
        mark_another_manager = true;

    spin_lock(&ksu_manager_list_write_lock);

    list_for_each_entry_safe (pos, tmp, &ksu_manager_appid_list, list) {
        if (pos->appid == appid) {
            list_del_rcu(&pos->list);
            spin_unlock(&ksu_manager_list_write_lock);
            kfree_rcu(pos, rcu);
            return;
        }

        if (mark_another_manager) {
            ksu_last_manager_appid = appid;
            mark_another_manager = false;
        }
    }

    spin_unlock(&ksu_manager_list_write_lock);

    if (mark_another_manager)
        ksu_last_manager_appid = KSU_INVALID_APPID;
    return;
}

void ksu_unregister_manager_by_signature_index(u8 signature_index)
{
    struct ksu_manager_node *node, *pos, *tmp;
    bool mark_another_manager = false;
    u16 last_each_alive_appid = KSU_INVALID_APPID;

    spin_lock(&ksu_manager_list_write_lock);

    list_for_each_entry_safe (pos, tmp, &ksu_manager_appid_list, list) {
        if (pos->signature_index == signature_index) {
            if (pos->appid == ksu_last_manager_appid) {
                mark_another_manager = true;
            }

            list_del_rcu(&pos->list);
            spin_unlock(&ksu_manager_list_write_lock);
            kfree_rcu(pos, rcu);
            return;
        }

        last_each_alive_appid = pos->appid;
    }

    spin_unlock(&ksu_manager_list_write_lock);

    if (mark_another_manager)
        ksu_last_manager_appid = last_each_alive_appid;
    return;
}

bool ksu_has_manager(void)
{
    bool empty;

    rcu_read_lock();
    empty = list_empty(&ksu_manager_appid_list);
    rcu_read_unlock();

    return !empty;
}

int ksu_handle_get_managers_cmd(struct ksu_get_managers_cmd __user *arg,
                                struct ksu_get_managers_cmd *cmd)
{
    struct ksu_manager_node *pos;
    int count = 0;
    u16 max_allowed = cmd->count;

    rcu_read_lock();
    list_for_each_entry_rcu (pos, &ksu_manager_appid_list, list) {
        if (count < max_allowed) {
            struct ksu_manager_entry entry = { .uid = pos->appid,
                                               .signature_index =
                                                   pos->signature_index };

            void __user *dest =
                (void __user *)((char *)arg +
                                sizeof(struct ksu_get_managers_cmd) +
                                (count * sizeof(struct ksu_manager_entry)));

            if (copy_to_user(dest, &entry, sizeof(entry))) {
                rcu_read_unlock();
                return -EFAULT;
            }
        }
        count++;
    }
    rcu_read_unlock();

    cmd->total_count = count;
    return 0;
}

int ksu_get_manager_signature_index_by_appid(u16 appid)
{
    struct ksu_manager_node *pos;

    rcu_read_lock();
    list_for_each_entry_rcu (pos, &ksu_manager_appid_list, list) {
        if (pos->appid == appid) {
            rcu_read_unlock();
            return pos->signature_index;
        }
    }
    rcu_read_unlock();
    return -ENODATA;
}
