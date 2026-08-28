package com.geekplus.common.query;

import java.io.Serializable;

/**
 * 动态查询单条条件（前端 conditionsJson 元素）
 * field: 实体字段名（camelCase）；op: 运算符；value: 条件值；column: 解析后的安全列名（仅服务端填充）
 */
public class QueryCondition implements Serializable {
    private static final long serialVersionUID = 1L;

    private String field;
    private String op;
    private Object value;
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

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }
}
