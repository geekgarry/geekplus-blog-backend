package com.geekplus.common.query;

import java.io.Serializable;

/**
 * 动态查询单条条件（前端 conditionsJson 元素）
 * <ul>
 *   <li>field: 实体属性名（camelCase）</li>
 *   <li>op: 运算符</li>
 *   <li>value: 条件值</li>
 *   <li>valueType: text/number/date/datetime/select/switch…（前端可传；否则后端按 Java 类型推断）</li>
 *   <li>column: 解析后的安全列名（仅服务端填充）</li>
 * </ul>
 */
public class QueryCondition implements Serializable {
    private static final long serialVersionUID = 1L;

    private String field;
    private String op;
    private Object value;
    /** 与前端 DynamicQueryForm valueType 对齐；影响 DynamicWhere 拼 SQL 方式 */
    private String valueType;
    /** MyBatis ${column} 白名单解析结果，禁止前端直接传入 */
    private String column;

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }
}
