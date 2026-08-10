package com.geekplus.framework.aspect;

import com.geekplus.common.annotation.DataScope;
import com.geekplus.common.domain.BaseEntity;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.webapp.system.entity.SysUser;
import com.geekplus.webapp.system.service.cache.RbacCacheService;
import com.geekplus.webapp.system.vo.SysDeptVO;
import com.geekplus.webapp.system.vo.SysRoleVO;
import org.apache.shiro.SecurityUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据权限过滤切面（若依风格）。
 * 自定部门优先读 RBAC 双重缓存中的部门 ID 列表。
 */
@Aspect
@Component
public class DataScopeAspect
{
    public static final String DATA_SCOPE_ALL = "1";
    public static final String DATA_SCOPE_CUSTOM = "2";
    public static final String DATA_SCOPE_DEPT = "3";
    public static final String DATA_SCOPE_DEPT_AND_CHILD = "4";
    public static final String DATA_SCOPE_SELF = "5";
    public static final String DATA_SCOPE = "dataScope";

    @Autowired
    private RbacCacheService rbacCacheService;

    @Before("@annotation(controllerDataScope)")
    public void doBefore(JoinPoint point, DataScope controllerDataScope) throws Throwable
    {
        clearDataScope(point);
        handleDataScope(point, controllerDataScope);
    }

    protected void handleDataScope(final JoinPoint joinPoint, DataScope controllerDataScope)
    {
        LoginUser loginUser = null;
        try
        {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            if (principal instanceof LoginUser)
            {
                loginUser = (LoginUser) principal;
            }
        }
        catch (Exception ignored)
        {
        }
        if (loginUser == null || loginUser.getUserId() == null)
        {
            return;
        }
        if (SysUser.isAdmin(loginUser.getUserId()))
        {
            return;
        }
        dataScopeFilter(joinPoint, loginUser, controllerDataScope.deptAlias(), controllerDataScope.userAlias());
    }

    public void dataScopeFilter(JoinPoint joinPoint, LoginUser user, String deptAlias, String userAlias)
    {
        StringBuilder sqlString = new StringBuilder();
        List<SysRoleVO> roles = user.getSysRoleList();
        if (roles == null || roles.isEmpty())
        {
            putDataScope(joinPoint, " AND (1 = 0) ");
            return;
        }

        String deptPrefix = StringUtils.isNotEmpty(deptAlias) ? deptAlias + "." : "";
        String userPrefix = StringUtils.isNotEmpty(userAlias) ? userAlias + "." : "";
        Long deptId = null;
        SysDeptVO dept = user.getSysDept();
        if (dept != null)
        {
            deptId = dept.getDeptId();
        }

        for (SysRoleVO role : roles)
        {
            String dataScope = role.getDataScope();
            if (StringUtils.isEmpty(dataScope))
            {
                continue;
            }
            if (DATA_SCOPE_ALL.equals(dataScope))
            {
                sqlString.setLength(0);
                break;
            }
            else if (DATA_SCOPE_CUSTOM.equals(dataScope))
            {
                List<Long> deptIds = rbacCacheService.getRoleDeptIds(role.getRoleId());
                if (deptIds != null && !deptIds.isEmpty())
                {
                    String in = deptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                    sqlString.append(StringUtils.format(" OR {}dept_id IN ( {} ) ", deptPrefix, in));
                }
                else
                {
                    sqlString.append(StringUtils.format(
                        " OR {}dept_id IN ( SELECT dept_id FROM sys_role_dept WHERE role_id = {} ) ",
                        deptPrefix, role.getRoleId()));
                }
            }
            else if (DATA_SCOPE_DEPT.equals(dataScope))
            {
                if (deptId != null)
                {
                    sqlString.append(StringUtils.format(" OR {}dept_id = {} ", deptPrefix, deptId));
                }
            }
            else if (DATA_SCOPE_DEPT_AND_CHILD.equals(dataScope))
            {
                if (deptId != null)
                {
                    sqlString.append(StringUtils.format(
                        " OR {}dept_id IN ( SELECT dept_id FROM sys_dept WHERE dept_id = {} OR find_in_set( {} , ancestors ) ) ",
                        deptPrefix, deptId, deptId));
                }
            }
            else if (DATA_SCOPE_SELF.equals(dataScope))
            {
                if (StringUtils.isNotEmpty(userAlias))
                {
                    sqlString.append(StringUtils.format(" OR {}user_id = {} ", userPrefix, user.getUserId()));
                }
                else
                {
                    sqlString.append(StringUtils.format(
                        " OR ({}create_by = '{}' OR {}user_id = {}) ",
                        userPrefix, user.getUsername(), userPrefix, user.getUserId()));
                }
            }
        }

        if (sqlString.length() > 0)
        {
            putDataScope(joinPoint, " AND (" + sqlString.substring(4) + ")");
        }
    }

    private static void putDataScope(JoinPoint joinPoint, String sql)
    {
        Object[] args = joinPoint.getArgs();
        if (args == null)
        {
            return;
        }
        for (Object arg : args)
        {
            if (arg == null)
            {
                continue;
            }
            if (arg instanceof BaseEntity)
            {
                ((BaseEntity) arg).getParams().put(DATA_SCOPE, sql);
                return;
            }
            if (arg instanceof SysUser)
            {
                ((SysUser) arg).getParams().put(DATA_SCOPE, sql);
                return;
            }
            try
            {
                Method m = arg.getClass().getMethod("getParams");
                Object params = m.invoke(arg);
                if (params instanceof Map)
                {
                    ((Map) params).put(DATA_SCOPE, sql);
                    return;
                }
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private void clearDataScope(final JoinPoint joinPoint)
    {
        Object[] args = joinPoint.getArgs();
        if (args == null)
        {
            return;
        }
        for (Object arg : args)
        {
            if (arg instanceof BaseEntity)
            {
                ((BaseEntity) arg).getParams().put(DATA_SCOPE, "");
            }
            else if (arg instanceof SysUser)
            {
                ((SysUser) arg).getParams().put(DATA_SCOPE, "");
            }
        }
    }
}
