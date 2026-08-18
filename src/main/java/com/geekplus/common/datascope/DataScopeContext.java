package com.geekplus.common.datascope;

/**
 * 数据权限 SQL 片段上下文（与 {@link com.geekplus.framework.aspect.DataScopeAspect} 配合）。
 * <p>
 * 优先写入查询参数 {@code params.dataScope}；本 ThreadLocal 作兜底，
 * 供 MyBatis 拦截器在 Mapper 未手写 {@code ${params.dataScope}} 时自动拼接。
 */
public final class DataScopeContext
{
    /** SQL 中的防重复注入标记 */
    public static final String SQL_MARKER = "/*GP_DATA_SCOPE*/";

    private static final ThreadLocal<String> SCOPE_SQL = new ThreadLocal<>();

    private DataScopeContext()
    {
    }

    public static void set(String sqlFragment)
    {
        SCOPE_SQL.set(sqlFragment);
    }

    public static String get()
    {
        return SCOPE_SQL.get();
    }

    public static void clear()
    {
        SCOPE_SQL.remove();
    }
}
