package com.geekplus.framework.aspect;

import com.geekplus.common.annotation.DataScope;
import com.geekplus.common.domain.BaseEntity;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.webapp.system.entity.SysUser;
import com.geekplus.webapp.system.vo.SysDeptVO;
import com.geekplus.webapp.system.vo.SysRoleVO;
import org.apache.shiro.SecurityUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * 数据权限过滤切面（若依风格）：
 * 将 SQL 片段写入实体 params.dataScope，供 Mapper ${params.dataScope} 拼接。
 * 支持 deptAlias / userAlias 为空（无表别名）的场景。
 */
@Aspect
@Component
public class DataScopeAspect
{
    /** 全部数据权限 */
    public static final String DATA_SCOPE_ALL = "1";
    /** 自定数据权限 */
    public static final String DATA_SCOPE_CUSTOM = "2";
    /** 部门数据权限 */
    public static final String DATA_SCOPE_DEPT = "3";
    /** 部门及以下数据权限 */
    public static final String DATA_SCOPE_DEPT_AND_CHILD = "4";
    /** 仅本人数据权限 */
    public static final String DATA_SCOPE_SELF = "5";

    /** 数据权限过滤关键字 */
    public static final String DATA_SCOPE = "dataScope";

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
            // 未登录或无 Subject 时跳过
        }
        if (loginUser == null || loginUser.getUserId() == null)
        {
            return;
        }
        // 超级管理员不过滤
        if (SysUser.isAdmin(loginUser.getUserId()))
        {
            return;
        }
        dataScopeFilter(joinPoint, loginUser, controllerDataScope.deptAlias(), controllerDataScope.userAlias());
    }

    /**
     * 按角色 dataScope 拼装过滤条件；多角色取 OR，任一角色为全部权限则不加过滤。
     */
    public static void dataScopeFilter(JoinPoint joinPoint, LoginUser user, String deptAlias, String userAlias)
    {
        StringBuilder sqlString = new StringBuilder();
        List<SysRoleVO> roles = user.getSysRoleList();
        if (roles == null || roles.isEmpty())
        {
            // 无角色：强制不可见（避免误放开）
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
                sqlString.append(StringUtils.format(
                    " OR {}dept_id IN ( SELECT dept_id FROM sys_role_dept WHERE role_id = {} ) ",
                    deptPrefix, role.getRoleId()));
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
                // 有用户别名时优先按 user_id；否则用 create_by / user_id（sys_user 列表无别名）
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

    /**
     * 写入 params.dataScope；兼容 BaseEntity 与 SysUser（未继承 BaseEntity）。
     */
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
            // 兜底：反射 getParams
            try
            {
                Method getParams = arg.getClass().getMethod("getParams");
                Object params = getParams.invoke(arg);
                if (params instanceof Map)
                {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) params;
                    map.put(DATA_SCOPE, sql);
                    return;
                }
            }
            catch (Exception ignored)
            {
            }
        }
    }

    /**
     * 防止前端传参污染 dataScope
     */
    private void clearDataScope(final JoinPoint joinPoint)
    {
        putDataScope(joinPoint, "");
    }
}
