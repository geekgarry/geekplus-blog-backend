package com.geekplus.common.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.geekplus.common.query.DynamicQueryHelper;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Plus Admin / GeekPlus 领域对象基类。
 * <p>
 * 承载两类能力：① 表级审计与软删字段；② 列表查询扩展（时间区间、排序、关键词、动态条件）。
 * 查询扩展由 {@link DynamicQueryHelper} 与 {@code mybatis/common/DynamicQueryMapper.xml} 统一消费。
 */
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------- 列表查询扩展（非表字段，仅请求入参） ----------

    /**
     * 顶栏/列表关键词：对 {@code params.searchColumns} 中列做 OR 模糊。
     * 与 {@link #conditionsJson} 互补——关键词扫多列；动态条件做精确字段+运算符。
     */
    private String searchValue;

    /** 排序字段（实体属性名 camelCase，经白名单映射为列名） */
    private String sortField;

    /** 排序方向：asc / desc */
    private String sortType;

    /**
     * 创建时间起（yyyy-MM-dd）。仅查询用，不序列化进响应体。
     */
    @JsonIgnore
    private String beginTime;

    /**
     * 创建时间止（yyyy-MM-dd）。仅查询用，不序列化进响应体。
     */
    @JsonIgnore
    private String endTime;

    /**
     * 动态条件 JSON 数组字符串，例如：
     * {@code [{"field":"username","op":"like","value":"admin"}]}
     * 列表 POST 由 Controller 从 Map/query/Header 写入；prepare 后进入 params.dq。
     */
    private String conditionsJson;

    /**
     * 查询扩展容器（服务端填充，勿由前端传嵌套对象）：
     * dq、orderByColumn、isAsc、searchColumns、createTimeColumn 等。
     */
    private Map<String, Object> params;

    /**
     * 行级数据权限过滤片段占位（由权限组件写入，Mapper 中按需拼接）。
     * 注意：{@code SysRole.dataScope} 是角色配置项，与本字段职责不同。
     */
    private String dataScope;

    // ---------- 表级公共列（多数业务表具备） ----------

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String remark;

    /** 删除标记：0 正常，1 已删 */
    private Integer delFlag;

    public String getSearchValue() {
        return searchValue;
    }

    public void setSearchValue(String searchValue) {
        this.searchValue = searchValue;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortType() {
        return sortType;
    }

    public void setSortType(String sortType) {
        this.sortType = sortType;
    }

    public String getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(String beginTime) {
        this.beginTime = beginTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getConditionsJson() {
        return conditionsJson;
    }

    public void setConditionsJson(String conditionsJson) {
        this.conditionsJson = conditionsJson;
    }

    public Map<String, Object> getParams() {
        if (params == null) {
            params = new HashMap<>();
        }
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public String getDataScope() {
        return dataScope;
    }

    public void setDataScope(String dataScope) {
        this.dataScope = dataScope;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}
