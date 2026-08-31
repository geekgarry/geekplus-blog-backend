package com.geekplus.common.query;

import java.util.Map;

/**
 * （可选）实体自行声明 field→列 白名单。
 * <p>
 * 推荐改在 Service 使用 {@link DynamicQueryHelper#prepare(com.geekplus.common.domain.BaseEntity, String, String...)}，
 * 本接口仅作兼容回退。
 */
public interface DynamicQueryColumns {

    /**
     * @return key=前端 field（camelCase），value=SQL 列名（可带表别名，如 su.username）
     */
    Map<String, String> dynamicQueryColumns();
}
