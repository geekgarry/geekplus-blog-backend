package com.geekplus.common.security;

import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.webapp.system.entity.SysUser;
import com.geekplus.webapp.system.vo.SysRoleVO;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 系统管理员判定。
 * <p>
 * <b>行级数据权限豁免</b>与「运维特权」分开：
 * <ul>
 *   <li>{@link #isDataScopeBypass}：仅超级管理员 {@code userId=1} 跳过行过滤（对齐系统）。
 *       普通「管理员」角色改 {@code data_scope} 必须生效，不能因 {@code role_key=admin} 永久看全库。</li>
 *   <li>{@link #canAssignAllDataScope}：可否把角色设为「全部数据权限」——允许 userId=1 / userType=1 / 超管 role_key。</li>
 * </ul>
 * 「全部数据权限」由 {@link com.geekplus.framework.aspect.DataScopeAspect} 读 RBAC 缓存的 {@code data_scope=1} 处理，
 * 不要再用会话里过期的 dataScope 做豁免。
 */
public final class AdminAuthUtils
{
    /** 小写 role_key 集合，O(1) 判定（用于运维特权，不用于行级豁免） */
    private static final Set<String> SYSTEM_ADMIN_ROLE_KEYS;

    static
    {
        Set<String> keys = new HashSet<>(8);
        keys.add("admin");
        keys.add("administrator");
        keys.add("system_admin");
        keys.add("sys_admin");
        SYSTEM_ADMIN_ROLE_KEYS = Collections.unmodifiableSet(keys);
    }

    private AdminAuthUtils()
    {
    }

    /**
     * 是否跳过行级数据权限过滤（列表 SQL / 树裁剪 / 单条 canAccess）。
     * 仅 {@code userId == 1}；其余账号一律按角色 {@code data_scope} 生效。
     */
    public static boolean isDataScopeBypass(LoginUser user)
    {
        if (user == null)
        {
            return false;
        }
        return SysUser.isAdmin(user.getUserId());
    }

    /**
     * 是否允许将角色数据范围设为「全部」(data_scope=1)，防止普通角色互相抬权。
     */
    public static boolean canAssignAllDataScope(LoginUser user)
    {
        if (user == null)
        {
            return false;
        }
        if (SysUser.isAdmin(user.getUserId()))
        {
            return true;
        }
        Integer userType = user.getUserType();
        if (userType != null && userType == 1)
        {
            return true;
        }
        return hasSystemAdminRoleKey(user);
    }

    public static boolean hasSystemAdminRoleKey(LoginUser user)
    {
        if (user == null)
        {
            return false;
        }
        List<SysRoleVO> roles = user.getSysRoleList();
        if (roles == null || roles.isEmpty())
        {
            return false;
        }
        for (SysRoleVO role : roles)
        {
            if (role != null && isSystemAdminRoleKey(role.getRoleKey()))
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isSystemAdminRoleKey(String roleKey)
    {
        if (StringUtils.isEmpty(roleKey))
        {
            return false;
        }
        return SYSTEM_ADMIN_ROLE_KEYS.contains(roleKey.trim().toLowerCase(Locale.ROOT));
    }
}
