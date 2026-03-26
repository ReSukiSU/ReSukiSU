#include <linux/types.h>

#include "uapi/supercall_internal.h"
#include "manager/manager_identity.h"
#include "policy/allowlist.h"
#include "feature/sulog.h"

bool only_manager(void)
{
    return is_manager();
}

bool only_root(void)
{
    return current_uid().val == 0;
}

bool manager_or_root(void)
{
    return current_uid().val == 0 || is_manager();
}

bool always_allow(void)
{
    return true;
}

bool allowed_for_su(void)
{
    bool is_allowed = is_manager() || ksu_is_allow_uid_for_current(current_uid().val);
#if __SULOG_GATE
    ksu_sulog_report_permission_check(current_uid().val, current->comm, is_allowed);
#endif
    return is_allowed;
}
