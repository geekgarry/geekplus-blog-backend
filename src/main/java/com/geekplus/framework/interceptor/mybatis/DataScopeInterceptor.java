package com.geekplus.framework.interceptor.mybatis;

import com.geekplus.common.datascope.DataScopeContext;
import com.geekplus.common.domain.BaseEntity;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.framework.aspect.DataScopeAspect;
import com.geekplus.webapp.system.entity.SysDept;
import com.geekplus.webapp.system.entity.SysUser;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;

/**
 * MyBatis 数据权限兜底插件（主路径仍是系统式 AOP 写 params + XML {@code ${params.dataScope}}）。
 * <p>
 * 必须同时拦截 4 参与 6 参 {@code Executor.query}：PageHelper 分页走 6 参（带 BoundSql），
 * 只拦 4 参会导致「列表分页时条件拼不进去」。
 */
@Component
@Intercepts({
    @Signature(type = Executor.class, method = "query", args = {
        MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
    }),
    @Signature(type = Executor.class, method = "query", args = {
        MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class
    })
})
public class DataScopeInterceptor implements Interceptor
{
    private static final Field BOUNDSQL_SQL_FIELD;

    static
    {
        try
        {
            BOUNDSQL_SQL_FIELD = BoundSql.class.getDeclaredField("sql");
            BOUNDSQL_SQL_FIELD.setAccessible(true);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("无法反射 BoundSql.sql", e);
        }
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable
    {
        Object[] args = invocation.getArgs();
        Object parameter = args[1];
        BoundSql boundSql = args.length == 6
            ? (BoundSql) args[5]
            : ((MappedStatement) args[0]).getBoundSql(parameter);

        String scopeSql = extractFromParameter(parameter);
        if (StringUtils.isEmpty(scopeSql) && !isPlainParamOnlyMap(parameter))
        {
            // 纯 @Param 查询（如 selectDeptIdsByRoleId）不套 ThreadLocal，避免二次查询误拼
            scopeSql = DataScopeContext.get();
        }
        if (StringUtils.isEmpty(scopeSql))
        {
            return invocation.proceed();
        }

        String original = boundSql.getSql();
        if (original != null && original.contains(DataScopeContext.SQL_MARKER))
        {
            // XML 已通过 ${params.dataScope} 拼过，避免重复
            return invocation.proceed();
        }

        String newSql = appendScope(original, scopeSql);
        BOUNDSQL_SQL_FIELD.set(boundSql, newSql);
        return invocation.proceed();
    }

    private static String extractFromParameter(Object parameter)
    {
        if (parameter == null)
        {
            return null;
        }
        if (parameter instanceof BaseEntity)
        {
            return stringVal(((BaseEntity) parameter).getParams());
        }
        if (parameter instanceof SysUser)
        {
            return stringVal(((SysUser) parameter).getParams());
        }
        if (parameter instanceof SysDept)
        {
            return stringVal(((SysDept) parameter).getParams());
        }
        if (parameter instanceof Map)
        {
            Map<?, ?> map = (Map<?, ?>) parameter;
            // PageHelper / 多参数时实体常在 map 的 value 里。
            // 注意：MyBatis ParamMap.get(缺失键) 会抛 BindingException，必须先 containsKey。
            Object direct = safeMapGet(map, DataScopeAspect.DATA_SCOPE);
            if (direct != null && StringUtils.isNotEmpty(String.valueOf(direct)))
            {
                return String.valueOf(direct);
            }
            Object params = safeMapGet(map, "params");
            if (params instanceof Map)
            {
                String v = stringVal((Map<?, ?>) params);
                if (StringUtils.isNotEmpty(v))
                {
                    return v;
                }
            }
            for (Object v : map.values())
            {
                if (v instanceof BaseEntity)
                {
                    String s = stringVal(((BaseEntity) v).getParams());
                    if (StringUtils.isNotEmpty(s))
                    {
                        return s;
                    }
                }
                if (v instanceof SysUser)
                {
                    String s = stringVal(((SysUser) v).getParams());
                    if (StringUtils.isNotEmpty(s))
                    {
                        return s;
                    }
                }
                if (v instanceof SysDept)
                {
                    String s = stringVal(((SysDept) v).getParams());
                    if (StringUtils.isNotEmpty(s))
                    {
                        return s;
                    }
                }
            }
        }
        return null;
    }

    /**
     * MyBatis {@code MapperMethod.ParamMap} 覆写了 {@code get}：键不存在即抛
     * {@code BindingException}（如 {@code selectDeptIdsByRoleId(roleId)} 只有 roleId/param1）。
     */
    private static Object safeMapGet(Map<?, ?> map, Object key)
    {
        if (map == null || key == null || !map.containsKey(key))
        {
            return null;
        }
        return map.get(key);
    }

    /** 仅有简单 @Param、无可承载 params.dataScope 的实体时返回 true */
    private static boolean isPlainParamOnlyMap(Object parameter)
    {
        if (!(parameter instanceof Map))
        {
            return false;
        }
        for (Object v : ((Map<?, ?>) parameter).values())
        {
            if (v instanceof BaseEntity || v instanceof SysUser || v instanceof SysDept)
            {
                return false;
            }
            if (v instanceof Map)
            {
                return false;
            }
        }
        return true;
    }

    private static String stringVal(Map<?, ?> params)
    {
        if (params == null)
        {
            return null;
        }
        Object v = safeMapGet(params, DataScopeAspect.DATA_SCOPE);
        if (v == null)
        {
            return null;
        }
        String s = String.valueOf(v);
        return StringUtils.isEmpty(s) ? null : s;
    }

    static String appendScope(String sql, String scopeSql)
    {
        if (sql == null)
        {
            return null;
        }
        String fragment = scopeSql.trim();
        if (!fragment.contains(DataScopeContext.SQL_MARKER))
        {
            fragment = " " + DataScopeContext.SQL_MARKER + " " + fragment;
        }
        String lower = sql.toLowerCase();
        int orderBy = lower.lastIndexOf(" order by ");
        if (orderBy > -1)
        {
            return sql.substring(0, orderBy) + " " + fragment + " " + sql.substring(orderBy);
        }
        if (lower.contains(" where "))
        {
            return sql + " " + fragment;
        }
        String body = fragment.replaceFirst("(?i)^\\s*and\\s+", " ");
        return sql + " WHERE 1=1 " + body;
    }

    @Override
    public Object plugin(Object target)
    {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties)
    {
    }
}
