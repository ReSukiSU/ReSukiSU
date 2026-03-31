#include <linux/types.h>

#include "supercall/internal.h"
#include "manager/manager_identity.h"
#include "policy/allowlist.h"
#include "feature/sulog.h"

#include "compat/kernel_compat.h"

bool only_manager(void)
{
    return is_manager();
}

bool only_root(void)
{
    return ksu_get_uid_t(current_uid()) == 0;
}

bool manager_or_root(void)
{
    return ksu_get_uid_t(current_uid()) == 0 || is_manager();
}

bool always_allow(void)
{
    return true;
}

bool allowed_for_su(void)
{
    bool is_allowed = is_manager() || ksu_is_allow_uid_for_current(ksu_get_uid_t(current_uid()));
    ksu_sulog_report_permission_check(ksu_get_uid_t(current_uid()), current->comm, is_allowed);

    return is_allowed;
}
