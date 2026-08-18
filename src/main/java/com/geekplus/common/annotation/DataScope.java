package com.geekplus.common.annotation;

import java.lang.annotation.*;

/**
 * 数据权限过滤注解（挂在 Service 查询方法上）。
 * <p>
 * 由 {@link com.geekplus.framework.aspect.DataScopeAspect} 按当前登录角色的 data_scope
 * 生成 SQL 片段，写入查询参数 {@code params.dataScope}。
 * <p>
 * <b>请挂在 Controller 列表/导出方法上</b>（入参为带 {@code params} 的实体），与系统一致；
 * 挂在 {@code @Transactional} Service 上可能因代理未织入而不生效。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope
{
    /**
     * 部门表别名，如 {@code su}、{@code d}；空表示无别名直接拼 {@code dept_id}
     */
    String deptAlias() default "";

    /**
     * 用户表别名（仅本人权限时拼 {@code user_id}）；空则回退 create_by / user_id
     */
    String userAlias() default "";
}
