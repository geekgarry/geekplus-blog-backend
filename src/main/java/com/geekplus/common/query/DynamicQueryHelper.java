package com.geekplus.common.query;

import com.alibaba.fastjson2.JSON;
import com.geekplus.common.domain.BaseEntity;
import com.geekplus.common.util.http.ServletUtil;
import com.geekplus.common.util.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 统一准备列表查询扩展条件。
 * <p>
 * 主路径：前端 {@code conditionsJson} 自选字段 → 服务端校验（实体属性存在 + 列名安全 + 敏感字段黑名单）
 * → {@code params.dq} → Mapper {@code DynamicWhere}（按 op 转 = / like / …）。<br>
 * 可选：{@link #prepare(BaseEntity, String, String...)} 传入 allowFields 可再收紧范围；
 * 不传则以前端所选、实体上存在的属性为准。<br>
 * {@link #applyKeywordColumns} 只服务 {@code searchValue}，与动态条件无关。
 */
public final class DynamicQueryHelper {

    private static final Logger log = LoggerFactory.getLogger(DynamicQueryHelper.class);

    public static final String HEADER_CONDITIONS_JSON = "X-GP-Conditions-Json";
    public static final String PARAM_DQ = "dq";
    public static final String PARAM_ORDER_COLUMN = "orderByColumn";
    public static final String PARAM_IS_ASC = "isAsc";
    public static final String PARAM_SEARCH_COLUMNS = "searchColumns";
    public static final String PARAM_CREATE_TIME_COLUMN = "createTimeColumn";

    private static final Set<String> ALLOWED_OPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "eq", "ne", "gt", "ge", "lt", "le", "like", "notLike", "isNull", "isNotNull"
    )));

    /** 禁止出现在动态条件里的属性（防注入 / 敏感列） */
    private static final Set<String> DENY_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "password", "params", "conditionsJson", "searchValue", "sortField", "sortType",
            "beginTime", "endTime", "dataScope", "deptIdList", "deptIds", "includeChildren",
            "sysRoleList", "sysDept", "roles", "roleIds", "token", "salt"
    )));

    private DynamicQueryHelper() {
    }

    public static void prepare(BaseEntity entity) {
        prepare(entity, null);
    }

    /**
     * @param tableAlias 表别名，如 {@code su}；无则传 null
     */
    public static void prepare(BaseEntity entity, String tableAlias) {
        prepare(entity, tableAlias, (String[]) null);
    }

    /**
     * 解析 conditionsJson 写入 params.dq。
     *
     * @param tableAlias  联表别名，可 null
     * @param allowFields 可选收紧：仅这些前端 field 可查；<b>null/空则前端选什么字段（实体上有的）就查什么</b>
     */
    public static void prepare(BaseEntity entity, String tableAlias, String... allowFields) {
        if (entity == null) {
            return;
        }
        fillConditionsJsonFromRequest(entity);

        Map<String, String> whitelist = buildWhitelist(allowFields);
        if (whitelist.isEmpty() && entity instanceof DynamicQueryColumns) {
            Map<String, String> fromEntity = ((DynamicQueryColumns) entity).dynamicQueryColumns();
            if (fromEntity != null && !fromEntity.isEmpty()) {
                whitelist = fromEntity;
            }
        }

        String json = entity.getConditionsJson();
        log.info("DynamicQueryHelper.prepare: entity={}, conditionsJson={}, mode={}",
                entity.getClass().getSimpleName(),
                json == null ? "null" : (json.length() > 160 ? json.substring(0, 160) + "…" : json),
                whitelist.isEmpty() ? "frontend-fields" : ("allowFields=" + whitelist.keySet()));
        if (StringUtils.isNotEmpty(json)) {
            List<QueryCondition> resolved = whitelist.isEmpty()
                    ? resolveFromEntity(json, entity, tableAlias)
                    : resolve(json, whitelist, tableAlias);
            if (!resolved.isEmpty()) {
                entity.getParams().put(PARAM_DQ, resolved);
                clearFlatFieldsCoveredByDq(entity, resolved);
                log.info("dynamic query ready: entity={}, dqSize={}, sample={}.{}={}",
                        entity.getClass().getSimpleName(),
                        resolved.size(),
                        resolved.get(0).getColumn(),
                        resolved.get(0).getOp(),
                        resolved.get(0).getValue());
            } else {
                log.warn("conditionsJson resolved empty: {}",
                        json.length() > 200 ? json.substring(0, 200) + "…" : json);
            }
        } else {
            log.debug("no conditionsJson on {} — params.dq will NOT be set", entity.getClass().getSimpleName());
        }

        Map<String, String> sortMap = whitelist.isEmpty() ? null : whitelist;
        applySort(entity, sortMap, tableAlias);
        if (sortMap == null) {
            applySortFromEntity(entity, tableAlias);
        }

        if (StringUtils.isNotEmpty(tableAlias)) {
            String ct = tableAlias + ".create_time";
            if (isSafeColumn(ct)) {
                entity.getParams().put(PARAM_CREATE_TIME_COLUMN, ct);
            }
        }
    }

    /**
     * 前端自选字段：field 须是实体可读属性、不在黑名单，列名 = camelCase→snake_case（可加表别名）。
     */
    public static List<QueryCondition> resolveFromEntity(String conditionsJson, BaseEntity entity, String tableAlias) {
        List<QueryCondition> raw = parseConditions(conditionsJson);
        if (raw.isEmpty() || entity == null) {
            return Collections.emptyList();
        }
        BeanWrapper bw = new BeanWrapperImpl(entity);
        List<QueryCondition> out = new ArrayList<>();
        for (QueryCondition c : raw) {
            if (c == null || StringUtils.isEmpty(c.getField()) || StringUtils.isEmpty(c.getOp())) {
                continue;
            }
            String field = c.getField().trim();
            if (DENY_FIELDS.contains(field)) {
                log.warn("skip condition: denied field={}", field);
                continue;
            }
            if (!bw.isReadableProperty(field)) {
                log.warn("skip condition: field={} not a property of {}", field, entity.getClass().getSimpleName());
                continue;
            }
            String op = c.getOp().trim();
            if (!ALLOWED_OPS.contains(op)) {
                log.warn("skip condition: unsupported op={} field={}", op, field);
                continue;
            }
            String column = camelToSnake(field);
            if (StringUtils.isNotEmpty(tableAlias) && column.indexOf('.') < 0) {
                column = tableAlias + "." + column;
            }
            if (!isSafeColumn(column)) {
                log.warn("skip condition: unsafe column from field={}", field);
                continue;
            }
            boolean nullOp = "isNull".equals(op) || "isNotNull".equals(op);
            if (!nullOp && (c.getValue() == null || "".equals(String.valueOf(c.getValue())))) {
                continue;
            }
            QueryCondition item = new QueryCondition();
            item.setField(field);
            item.setOp(op);
            item.setValue(c.getValue());
            item.setColumn(column);
            item.setValueType(resolveValueType(c.getValueType(), bw.getPropertyType(field)));
            out.add(item);
        }
        return out;
    }

    /**
     * 优先用前端传入的 valueType；否则按 Java 属性类型推断（Date→datetime、数字→number、其余 text）。
     */
    public static String resolveValueType(String fromFront, Class<?> javaType) {
        if (StringUtils.isNotEmpty(fromFront)) {
            String vt = fromFront.trim().toLowerCase(Locale.ROOT);
            if ("text".equals(vt) || "textarea".equals(vt) || "number".equals(vt)
                    || "date".equals(vt) || "datetime".equals(vt)
                    || "select".equals(vt) || "switch".equals(vt)) {
                return vt;
            }
        }
        return inferValueTypeFromJava(javaType);
    }

    public static String inferValueTypeFromJava(Class<?> javaType) {
        if (javaType == null) {
            return "text";
        }
        if (Date.class.isAssignableFrom(javaType)
                || javaType.getName().startsWith("java.time.")) {
            // LocalDate → date；LocalDateTime/Instant/Date → datetime
            if ("java.time.LocalDate".equals(javaType.getName())) {
                return "date";
            }
            return "datetime";
        }
        if (Number.class.isAssignableFrom(javaType)
                || javaType == int.class || javaType == long.class
                || javaType == double.class || javaType == float.class
                || javaType == short.class || javaType == byte.class
                || BigDecimal.class.isAssignableFrom(javaType)) {
            return "number";
        }
        if (javaType == boolean.class || Boolean.class.isAssignableFrom(javaType)) {
            return "switch";
        }
        return "text";
    }

    private static void applySortFromEntity(BaseEntity entity, String tableAlias) {
        if (entity == null || StringUtils.isEmpty(entity.getSortField())) {
            return;
        }
        String field = entity.getSortField().trim();
        if (DENY_FIELDS.contains(field)) {
            return;
        }
        BeanWrapper bw = new BeanWrapperImpl(entity);
        if (!bw.isReadableProperty(field)) {
            return;
        }
        String column = camelToSnake(field);
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

    private static List<QueryCondition> parseConditions(String conditionsJson) {
        if (StringUtils.isEmpty(conditionsJson)) {
            return Collections.emptyList();
        }
        String json = conditionsJson.trim();
        if (json.length() >= 2 && json.charAt(0) == '"' && json.charAt(json.length() - 1) == '"') {
            try {
                String unwrapped = JSON.parseObject(json, String.class);
                if (StringUtils.isNotEmpty(unwrapped)) {
                    json = unwrapped.trim();
                }
            } catch (Exception ignored) {
                // keep original
            }
        }
        try {
            List<QueryCondition> raw = JSON.parseArray(json, QueryCondition.class);
            return raw != null ? raw : Collections.emptyList();
        } catch (Exception e) {
            log.warn("invalid conditionsJson: {} | raw={}", e.getMessage(),
                    json.length() > 180 ? json.substring(0, 180) + "…" : json);
            return Collections.emptyList();
        }
    }

    /**
     * 由字段名列表构建白名单：{@code phoneNumber} → {@code phone_number}；
     * 已是 snake 或 {@code alias.col} 则原样作为列名，Map 的 key 仍用属性名（点号后一段）。
     */
    public static Map<String, String> buildWhitelist(String... allowFields) {
        if (allowFields == null || allowFields.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String raw : allowFields) {
            if (StringUtils.isEmpty(raw)) {
                continue;
            }
            String token = raw.trim();
            String fieldKey;
            String column;
            int dot = token.lastIndexOf('.');
            if (dot > 0) {
                String alias = token.substring(0, dot);
                String colPart = token.substring(dot + 1);
                fieldKey = colPart;
                column = alias + "." + camelToSnake(colPart);
            } else {
                fieldKey = token;
                column = camelToSnake(token);
            }
            if (!isSafeColumn(column)) {
                log.warn("skip unsafe allowField: {}", token);
                continue;
            }
            map.put(fieldKey, column);
            // 兼容前端误传 snake_case field
            String snakeKey = camelToSnake(fieldKey);
            if (!snakeKey.equals(fieldKey)) {
                map.putIfAbsent(snakeKey, column);
            }
        }
        return map;
    }

    /** camelCase → snake_case；已含 _ 则小写返回 */
    static String camelToSnake(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.indexOf('_') >= 0) {
            return name.toLowerCase(Locale.ROOT);
        }
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 实体未绑上时，从 query / Header 补全 */
    static void fillConditionsJsonFromRequest(BaseEntity entity) {
        if (entity == null || StringUtils.isNotEmpty(entity.getConditionsJson())) {
            return;
        }
        try {
            HttpServletRequest request = ServletUtil.getRequest();
            if (request == null) {
                return;
            }
            String fromParam = request.getParameter("conditionsJson");
            if (StringUtils.isNotEmpty(fromParam)) {
                entity.setConditionsJson(fromParam);
                return;
            }
            String fromHeader = request.getHeader(HEADER_CONDITIONS_JSON);
            if (StringUtils.isNotEmpty(fromHeader)) {
                entity.setConditionsJson(fromHeader);
            }
        } catch (Exception e) {
            log.debug("fillConditionsJsonFromRequest skipped: {}", e.getMessage());
        }
    }

    /**
     * 仅用于 {@code searchValue} 多列 OR（KeywordSearch），列名须已是 SQL 列（可带别名）。
     * <b>不会</b>影响 conditionsJson / params.dq。
     */
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
        if (fieldColumnMap == null || fieldColumnMap.isEmpty()) {
            return Collections.emptyList();
        }
        List<QueryCondition> raw = parseConditions(conditionsJson);
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<QueryCondition> out = new ArrayList<>();
        for (QueryCondition c : raw) {
            if (c == null || StringUtils.isEmpty(c.getField()) || StringUtils.isEmpty(c.getOp())) {
                continue;
            }
            String op = c.getOp().trim();
            if (!ALLOWED_OPS.contains(op)) {
                log.warn("skip condition: unsupported op={} field={}", op, c.getField());
                continue;
            }
            String column = fieldColumnMap.get(c.getField());
            if (StringUtils.isEmpty(column)) {
                log.warn("skip condition: field={} not in allowFields {}", c.getField(), fieldColumnMap.keySet());
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
            item.setValueType(resolveValueType(c.getValueType(), null));
            out.add(item);
        }
        return out;
    }

    private static void clearFlatFieldsCoveredByDq(BaseEntity entity, List<QueryCondition> resolved) {
        if (entity == null || resolved == null || resolved.isEmpty()) {
            return;
        }
        try {
            BeanWrapper bw = new BeanWrapperImpl(entity);
            for (QueryCondition c : resolved) {
                if (c == null || StringUtils.isEmpty(c.getField())) {
                    continue;
                }
                String field = c.getField();
                if (!bw.isWritableProperty(field)) {
                    continue;
                }
                try {
                    bw.setPropertyValue(field, null);
                } catch (BeansException ignored) {
                    // ignore
                }
            }
        } catch (Exception e) {
            log.debug("clearFlatFieldsCoveredByDq skipped: {}", e.getMessage());
        }
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
