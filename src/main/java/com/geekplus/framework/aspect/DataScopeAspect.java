package com.geekplus.framework.aspect;

import com.geekplus.common.annotation.DataScope;
import com.geekplus.common.datascope.DataScopeContext;
import com.geekplus.common.domain.BaseEntity;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.security.AdminAuthUtils;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.webapp.system.entity.SysDept;
import com.geekplus.webapp.system.entity.SysUser;
import com.geekplus.webapp.system.service.cache.RbacCacheService;
import com.geekplus.webapp.system.vo.SysDeptVO;
import com.geekplus.webapp.system.vo.SysRoleVO;
import org.apache.shiro.SecurityUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * 数据权限切面（对齐系统）：挂在 <b>Controller</b> 列表方法上，进入前把过滤 SQL 写入入参
 * {@code params.dataScope}；Mapper XML 用 {@code ${params.dataScope}} 拼接；ThreadLocal 供 MyBatis 插件兜底。
 * <p>
 * 不要只挂在带 {@code @Transactional} 的 Service 上——Shiro/事务代理下切面可能未织入或二次 clear 会清空已写入片段。
 * 使用 {@code @Around} + finally 清理 ThreadLocal，避免线程池泄漏。
 */
@Aspect
@Order(2)
@Component
public class DataScopeAspect
{
    public static final String DATA_SCOPE_ALL = "1";
    public static final String DATA_SCOPE_CUSTOM = "2";
    public static final String DATA_SCOPE_DEPT = "3";
    public static final String DATA_SCOPE_DEPT_AND_CHILD = "4";
    public static final String DATA_SCOPE_SELF = "5";
    public static final String DATA_SCOPE = "dataScope";

    private static final String DENY_SQL = " AND " + DataScopeContext.SQL_MARKER + " (1 = 0) ";

    @Autowired
    private RbacCacheService rbacCacheService;

    @Around("@annotation(controllerDataScope)")
    public Object around(ProceedingJoinPoint point, DataScope controllerDataScope) throws Throwable
    {
        clearDataScope(point);
        try
        {
            handleDataScope(point, controllerDataScope);
            return point.proceed();
        }
        finally
        {
            // 只清 ThreadLocal；params 上的片段留给本次 SQL（含 PageHelper count）使用完即随请求结束
            DataScopeContext.clear();
        }
    }

    protected void handleDataScope(final JoinPoint joinPoint, DataScope controllerDataScope)
    {
        LoginUser loginUser = currentLoginUser();
        if (loginUser == null || loginUser.getUserId() == null)
        {
            return;
        }
        if (AdminAuthUtils.isDataScopeBypass(loginUser))
        {
            return;
        }
        dataScopeFilter(joinPoint, loginUser, controllerDataScope.deptAlias(), controllerDataScope.userAlias());
    }

    public void dataScopeFilter(JoinPoint joinPoint, LoginUser user, String deptAlias, String userAlias)
    {
        List<SysRoleVO> roles = user.getSysRoleList();
        if (roles == null || roles.isEmpty())
        {
            putDataScope(joinPoint, DENY_SQL);
            return;
        }

        String deptPrefix = StringUtils.isNotEmpty(deptAlias) ? deptAlias + "." : "";
        String userPrefix = StringUtils.isNotEmpty(userAlias) ? userAlias + "." : "";
        Long deptId = resolveDeptId(user);
        StringBuilder sql = new StringBuilder(128);

        for (SysRoleVO role : roles)
        {
            if (role == null)
            {
                continue;
            }
            String dataScope = resolveScope(role);
            if (DATA_SCOPE_ALL.equals(dataScope))
            {
                return;
            }
            appendScopeClause(sql, dataScope, role, deptPrefix, userPrefix, deptId, user);
        }

        if (sql.length() > 0)
        {
            // 系统风格：AND ( ... )，再加本项目防重复标记
            putDataScope(joinPoint, " AND " + DataScopeContext.SQL_MARKER + " (" + sql.substring(4) + ")");
        }
        else
        {
            putDataScope(joinPoint, DENY_SQL);
        }
    }

    private String resolveScope(SysRoleVO role)
    {
        String dataScope = rbacCacheService.getRoleDataScope(role.getRoleId());
        if (StringUtils.isEmpty(dataScope) && StringUtils.isNotEmpty(role.getDataScope()))
        {
            dataScope = role.getDataScope();
        }
        return StringUtils.isEmpty(dataScope) ? DATA_SCOPE_SELF : dataScope;
    }

    private void appendScopeClause(StringBuilder sql, String dataScope, SysRoleVO role,
                                   String deptPrefix, String userPrefix, Long deptId, LoginUser user)
    {
        if (DATA_SCOPE_CUSTOM.equals(dataScope))
        {
            List<Long> deptIds = rbacCacheService.getRoleDeptIds(role.getRoleId());
            if (deptIds != null && !deptIds.isEmpty())
            {
                sql.append(" OR ").append(deptPrefix).append("dept_id IN (");
                for (int i = 0; i < deptIds.size(); i++)
                {
                    if (i > 0)
                    {
                        sql.append(',');
                    }
                    sql.append(deptIds.get(i));
                }
                sql.append(") ");
            }
            else
            {
                sql.append(StringUtils.format(
                    " OR {}dept_id IN ( SELECT dept_id FROM sys_role_dept WHERE role_id = {} ) ",
                    deptPrefix, role.getRoleId()));
            }
        }
        else if (DATA_SCOPE_DEPT.equals(dataScope))
        {
            if (deptId != null)
            {
                sql.append(StringUtils.format(" OR {}dept_id = {} ", deptPrefix, deptId));
            }
            else
            {
                sql.append(" OR 1 = 0 ");
            }
        }
        else if (DATA_SCOPE_DEPT_AND_CHILD.equals(dataScope))
        {
            if (deptId != null)
            {
                sql.append(StringUtils.format(
                    " OR {}dept_id IN ( SELECT dept_id FROM sys_dept WHERE dept_id = {} OR find_in_set( {} , ancestors ) ) ",
                    deptPrefix, deptId, deptId));
            }
            else
            {
                sql.append(" OR 1 = 0 ");
            }
        }
        else if (DATA_SCOPE_SELF.equals(dataScope))
        {
            if (StringUtils.isNotEmpty(userPrefix))
            {
                sql.append(StringUtils.format(" OR {}user_id = {} ", userPrefix, user.getUserId()));
            }
            else if (StringUtils.isNotEmpty(deptPrefix))
            {
                if (deptId != null)
                {
                    sql.append(StringUtils.format(" OR {}dept_id = {} ", deptPrefix, deptId));
                }
                else
                {
                    sql.append(" OR 1 = 0 ");
                }
            }
            else
            {
                sql.append(StringUtils.format(
                    " OR (create_by = '{}' OR user_id = {}) ",
                    user.getUsername(), user.getUserId()));
            }
        }
    }

    private static Long resolveDeptId(LoginUser user)
    {
        if (user == null)
        {
            return null;
        }
        SysDeptVO dept = user.getSysDept();
        if (dept != null && dept.getDeptId() != null)
        {
            return dept.getDeptId();
        }
        return user.getDeptId();
    }

    private static LoginUser currentLoginUser()
    {
        try
        {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            if (principal instanceof LoginUser)
            {
                return (LoginUser) principal;
            }
        }
        catch (Exception ignored)
        {
        }
        return null;
    }

    /**
     * 优先第一个入参的 params；并写入 ThreadLocal 供插件兜底。
     */
    private static void putDataScope(JoinPoint joinPoint, String sql)
    {
        DataScopeContext.set(sql);
        Map<String, Object> params = findParams(joinPoint);
        if (params != null)
        {
            params.put(DATA_SCOPE, sql);
        }
    }

    /** 拼接前清空，防止前端伪造 params.dataScope 注入 */
    private void clearDataScope(final JoinPoint joinPoint)
    {
        DataScopeContext.clear();
        Map<String, Object> params = findParams(joinPoint);
        if (params != null)
        {
            params.put(DATA_SCOPE, "");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findParams(JoinPoint joinPoint)
    {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0 || args[0] == null)
        {
            return null;
        }
        // 约定第一个参数带 params；优先具体实体，避免与 BaseEntity 字段混淆
        Object arg = args[0];
        if (arg instanceof SysUser)
        {
            return ((SysUser) arg).getParams();
        }
        if (arg instanceof SysDept)
        {
            return ((SysDept) arg).getParams();
        }
        if (arg instanceof BaseEntity)
        {
            return ((BaseEntity) arg).getParams();
        }
        try
        {
            Method m = arg.getClass().getMethod("getParams");
            Object params = m.invoke(arg);
            if (params instanceof Map)
            {
                return (Map<String, Object>) params;
            }
        }
        catch (Exception ignored)
        {
        }
        return null;
    }
}
