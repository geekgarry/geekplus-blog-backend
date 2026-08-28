package com.geekplus.common.query;

import java.util.Map;

/**
 * 实体声明允许参与动态条件查询的字段 → 列名映射（防 SQL 注入）
 */
public interface DynamicQueryColumns {

    /**
     * @return key=前端 field（camelCase），value=SQL 列名（可带表别名，如 su.username）
     */
    Map<String, String> dynamicQueryColumns();
}
