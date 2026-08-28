package com.geekplus.common.query;

import com.alibaba.fastjson.JSON;
import com.geekplus.common.domain.BaseEntity;
import com.geekplus.common.util.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一准备列表查询扩展条件：
 * <ul>
 *   <li>conditionsJson → params.dq（DynamicWhere）</li>
 *   <li>sortField/sortType → params.orderByColumn/isAsc（OrderBy）</li>
 *   <li>tableAlias → params.createTimeColumn（CreateTimeRangeAliased）</li>
 * </ul>
 * 前端 GET 传 conditionsJson 字符串，勿传嵌套数组。
 */
public final class DynamicQueryHelper {

    private static final Logger log = LoggerFactory.getLogger(DynamicQueryHelper.class);

    public static final String PARAM_DQ = "dq";
    public static final String PARAM_ORDER_COLUMN = "orderByColumn";
    public static final String PARAM_IS_ASC = "isAsc";
    public static final String PARAM_SEARCH_COLUMNS = "searchColumns";
    public static final String PARAM_CREATE_TIME_COLUMN = "createTimeColumn";

    private static final Set<String> ALLOWED_OPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "eq", "ne", "gt", "ge", "lt", "le", "like", "notLike", "isNull", "isNotNull"
    )));

    private DynamicQueryHelper() {
    }

    public static void prepare(BaseEntity entity) {
        prepare(entity, null);
    }

    /**
     * @param tableAlias 联表别名（如 "su"）；同时用于排序列与 create_time 前缀
     */
    public static void prepare(BaseEntity entity, String tableAlias) {
        if (entity == null) {
            return;
        }
        Map<String, String> whitelist = null;
        if (entity instanceof DynamicQueryColumns) {
            whitelist = ((DynamicQueryColumns) entity).dynamicQueryColumns();
        }

        String json = entity.getConditionsJson();
        if (StringUtils.isNotEmpty(json)) {
            if (whitelist == null || whitelist.isEmpty()) {
                log.debug("skip conditionsJson: {} has no DynamicQueryColumns", entity.getClass().getSimpleName());
            } else {
                List<QueryCondition> resolved = resolve(json, whitelist, tableAlias);
                if (!resolved.isEmpty()) {
                    entity.getParams().put(PARAM_DQ, resolved);
                }
            }
        }

        applySort(entity, whitelist, tableAlias);

        if (StringUtils.isNotEmpty(tableAlias)) {
            String ct = tableAlias + ".create_time";
            if (isSafeColumn(ct)) {
                entity.getParams().put(PARAM_CREATE_TIME_COLUMN, ct);
            }
        }
    }

    /** 为 KeywordSearch 设置可模糊的列（须已是安全列名，可带别名） */
    public static void applyKeywordColumns(BaseEntity entity, String... columns) {
        if (entity == null || columns == null || columns.length == 0) {
            return;
        }
        List<String> safe = new ArrayList<>();
        for (String c : columns) {
            if (isSafeColumn(c)) {
                safe.add(c);
            }
        }
        if (!safe.isEmpty()) {
            entity.getParams().put(PARAM_SEARCH_COLUMNS, safe);
        }
    }

    public static void applySort(BaseEntity entity, Map<String, String> fieldColumnMap, String tableAlias) {
        if (entity == null || StringUtils.isEmpty(entity.getSortField())) {
            return;
        }
        if (fieldColumnMap == null || fieldColumnMap.isEmpty()) {
            return;
        }
        String column = fieldColumnMap.get(entity.getSortField());
        if (StringUtils.isEmpty(column)) {
            return;
        }
        if (StringUtils.isNotEmpty(tableAlias) && column.indexOf('.') < 0) {
            column = tableAlias + "." + column;
        }
        if (!isSafeColumn(column)) {
            return;
        }
        entity.getParams().put(PARAM_ORDER_COLUMN, column);
        String dir = entity.getSortType();
        entity.getParams().put(PARAM_IS_ASC, "desc".equalsIgnoreCase(dir) ? "desc" : "asc");
    }

    public static List<QueryCondition> resolve(String conditionsJson, Map<String, String> fieldColumnMap) {
        return resolve(conditionsJson, fieldColumnMap, null);
    }

    public static List<QueryCondition> resolve(String conditionsJson, Map<String, String> fieldColumnMap, String tableAlias) {
        if (StringUtils.isEmpty(conditionsJson) || fieldColumnMap == null || fieldColumnMap.isEmpty()) {
            return Collections.emptyList();
        }
        List<QueryCondition> raw;
        try {
            raw = JSON.parseArray(conditionsJson, QueryCondition.class);
        } catch (Exception e) {
            log.warn("invalid conditionsJson: {}", e.getMessage());
            return Collections.emptyList();
        }
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<QueryCondition> out = new ArrayList<>();
        for (QueryCondition c : raw) {
            if (c == null || StringUtils.isEmpty(c.getField()) || StringUtils.isEmpty(c.getOp())) {
                continue;
            }
            String op = c.getOp().trim();
            if (!ALLOWED_OPS.contains(op)) {
                continue;
            }
            String column = fieldColumnMap.get(c.getField());
            if (StringUtils.isEmpty(column)) {
                continue;
            }
            if (StringUtils.isNotEmpty(tableAlias) && column.indexOf('.') < 0) {
                column = tableAlias + "." + column;
            }
            if (!isSafeColumn(column)) {
                continue;
            }
            boolean nullOp = "isNull".equals(op) || "isNotNull".equals(op);
            if (!nullOp && (c.getValue() == null || "".equals(String.valueOf(c.getValue())))) {
                continue;
            }
            QueryCondition item = new QueryCondition();
            item.setField(c.getField());
            item.setOp(op);
            item.setValue(c.getValue());
            item.setColumn(column);
            out.add(item);
        }
        return out;
    }

    static boolean isSafeColumn(String column) {
        return column != null && column.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?$");
    }

    public static boolean hasDynamicConditions(BaseEntity entity) {
        if (entity == null || entity.getParams() == null) {
            return false;
        }
        Object dq = entity.getParams().get(PARAM_DQ);
        return dq instanceof List && !((List<?>) dq).isEmpty();
    }
}
