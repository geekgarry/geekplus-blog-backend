<!-- 扁平条件查询（Element UI）：字段按 queryValueType 适配输入；BaseEntity 含创建时间区间/searchValue；列表 POST 只传扁平字段，不与 conditionsJson 同传 -->
<template>
    <div class="app-container">
            <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="88px">
<#if allColumn?exists>
<#list allColumn as column>
<#if column.isPk!='1' && column.smallColumnName != 'password' && column.smallColumnName != 'delFlag'>
                <el-form-item label="${column.columnComment}" prop="${column.smallColumnName}">
<#if column.queryValueType == 'number'>
                    <el-input-number v-model="queryParams.${column.smallColumnName}" controls-position="right" style="width: 180px" />
<#elseif column.queryValueType == 'date'>
                    <el-date-picker v-model="queryParams.${column.smallColumnName}" type="date" value-format="yyyy-MM-dd" placeholder="选择日期" clearable style="width: 180px" />
<#elseif column.queryValueType == 'datetime'>
                    <el-date-picker v-model="queryParams.${column.smallColumnName}" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="选择日期时间" clearable style="width: 200px" />
<#elseif column.queryValueType == 'select' || column.queryValueType == 'switch'>
                    <el-select v-model="queryParams.${column.smallColumnName}" placeholder="请选择" clearable style="width: 180px">
                        <el-option label="正常/是" :value="0" />
                        <el-option label="停用/否" :value="1" />
                    </el-select>
<#else>
                    <el-input v-model="queryParams.${column.smallColumnName}" clearable placeholder="请输入${column.columnComment}" style="width: 180px" @keyup.enter.native="handleQuery" />
</#if>
                </el-form-item>
</#if>
</#list>
</#if>
                <el-form-item label="创建时间">
                    <el-date-picker v-model="dateRange" type="daterange" value-format="yyyy-MM-dd" range-separator="-" start-placeholder="开始" end-placeholder="结束" style="width: 240px" @change="onDateRangeChange" />
                </el-form-item>
                <el-form-item label="关键词" prop="searchValue">
                    <el-input v-model="queryParams.searchValue" clearable placeholder="多字段关键词" style="width: 180px" @keyup.enter.native="handleQuery" />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
                    <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
                </el-form-item>
            </el-form>

        <el-row :gutter="10" class="mb8">
            <el-col :span="1.5">
                <el-button type="primary" icon="el-icon-plus" size="mini" @click="handleAdd">新增</el-button>
            </el-col>
            <el-col :span="1.5">
                <el-button type="success" icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate">修改</el-button>
            </el-col>
            <el-col :span="1.5">
                <el-button type="danger" icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete">删除</el-button>
            </el-col>
            <el-col :span="1.5">
                <el-button type="warning" icon="el-icon-download" size="mini" @click="handleExport">导出</el-button>
            </el-col>
            <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>

        <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="55" align="center" />
            <#if allColumn?exists>
            <#list allColumn as column>
            <#if column.javaType == 'Date'>
            <el-table-column label="${column.columnComment}" align="center" prop="${column.smallColumnName}" width="100" :show-overflow-tooltip="true" >
                <template slot-scope="scope">
                    <span>{{ dateFormat(scope.row.${column.smallColumnName}) }}</span>
                </template>
            </el-table-column>
            <#else >
            <el-table-column label="${column.columnComment}" align="center" prop="${column.smallColumnName}" />
            </#if>
            </#list>
            </#if>
            <el-table-column label="操作" align="center" fixed="right" width="120" class-name="small-padding fixed-width">
                <template slot-scope="scope">
                    <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)">修改</el-button>
                    <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

        <el-dialog :title="title" :visible.sync="open" width="780px" append-to-body>
            <el-form ref="form" :model="form" :rules="rules" label-width="80px">
                <el-row>
                    <#if allColumn?exists>
                    <#list allColumn as column>
                    <#if (column.columnDataType=='varchar'||column.columnDataType=='char') && column.isPk!='1' >
                    <el-col :span="12">
                        <el-form-item label="${column.columnComment}" prop="${column.smallColumnName}">
                            <el-input v-model="form.${column.smallColumnName}" placeholder="请输入${column.columnComment}" />
                        </el-form-item>
                    </el-col>
                    <#elseif (column.columnDataType=='int'||column.columnDataType=='tinyint'||column.columnDataType=='smallint'||column.columnDataType=='bigint') && column.isPk!='1' >
                    <el-col :span="12">
                        <el-form-item label="${column.columnComment}" prop="${column.smallColumnName}">
                            <el-select v-model="form.${column.smallColumnName}" placeholder="请选择">
                                <el-option label="Label1" value="1"></el-option>
                                <el-option label="Label2" value="2"></el-option>
                            </el-select>
                        </el-form-item>
                    </el-col>
                    <#elseif (column.columnDataType=='text'||column.columnDataType=='tinytext'||column.columnDataType=='bigtext'||column.columnDataType=='longtext') && column.isPk!='1' >
                    <el-col :span="24">
                        <el-form-item label="${column.columnComment}" prop="${column.smallColumnName}">
                            <el-input type="textarea" :rows="3" v-model="form.${column.smallColumnName}" placeholder="请输入内容"></el-input>
                        </el-form-item>
                    </el-col>
                    </#if>
                    </#list>
                    </#if>
                </el-row>
            </el-form>
            <div slot="footer" class="dialog-footer">
                <el-button type="primary" @click="submitForm">确 定</el-button>
                <el-button @click="cancel">取 消</el-button>
            </div>
        </el-dialog>
    </div>
</template>

<script>
import { list${className}, get${className}, del${className}, add${className}, update${className}, export${className} } from "@/api/${moduleName}/${jsMethodName}";

export default {
    name: "${componentName}",
    data() {
        return {
            loading: true,
            ids: [],
            single: true,
            multiple: true,
            showSearch: true,
            total: 0,
            list: [],
            title: "",
            open: false,
            dateRange: [],
            queryParams: {
                pageNum: 1,
                pageSize: 10,
                searchValue: undefined,
                beginTime: undefined,
                endTime: undefined,
                sortField: undefined,
                sortType: undefined,
<#if allColumn?exists>
<#list allColumn as column>
<#if column.isPk!='1' && column.smallColumnName != 'password' && column.smallColumnName != 'delFlag'>
                ${column.smallColumnName}: undefined,
</#if>
</#list>
</#if>
            },
            form: {},
            rules: {
            <#if allColumn?exists>
            <#list allColumn as column>
            <#if column.javaType == 'String'>
                ${column.smallColumnName}: [
                { required: true, message: "${column.columnComment}不能为空", trigger: "blur" }
                ],
            <#elseif column.javaType == 'Integer'>
                ${column.smallColumnName}: [
                { required: true, message: "${column.columnComment}不能为空", trigger: "change" }
                ],
            </#if>
            </#list>
            </#if>
            }
        };
    },
    created() {
        this.getList();
    },
    methods: {
        /** 扁平查询：只传非空筛选项 + 分页，不传 conditionsJson */
        getList() {
            this.loading = true;
            const q = this.queryParams;
            const req = { pageNum: q.pageNum, pageSize: q.pageSize };
            Object.keys(q).forEach((k) => {
                if (k === 'pageNum' || k === 'pageSize') return;
                const v = q[k];
                if (v !== undefined && v !== null && v !== '') req[k] = v;
            });
            list${className}(req).then(response => {
                this.list = response.rows;
                this.total = response.total;
                this.loading = false;
            }).catch(() => { this.loading = false; });
        },
        onDateRangeChange(val) {
            if (val && val.length === 2) {
                this.queryParams.beginTime = val[0];
                this.queryParams.endTime = val[1];
            } else {
                this.queryParams.beginTime = undefined;
                this.queryParams.endTime = undefined;
            }
        },
        cancel() {
            this.open = false;
            this.reset();
        },
        reset() {
            this.form = {
            <#if allColumn?exists>
                <#list allColumn as column>
                ${column.smallColumnName}: undefined,
                </#list>
            </#if>
            };
            this.resetForm("form");
        },
        handleQuery() {
            this.queryParams.pageNum = 1;
            this.getList();
        },
        resetQuery() {
            this.dateRange = [];
            this.resetForm("queryForm");
            this.queryParams.beginTime = undefined;
            this.queryParams.endTime = undefined;
            this.handleQuery();
        },
        handleSelectionChange(selection) {
            this.ids = selection.map(item => item.${pkColumn.smallColumnName})
            this.single = selection.length!=1
            this.multiple = !selection.length
        },
        handleAdd() {
            this.reset();
            this.open = true;
            this.title = "添加数据信息";
        },
        handleUpdate(row) {
            this.reset();
            const id = row.${pkColumn.smallColumnName} || this.ids[0]
            get${className}({ ${pkColumn.smallColumnName}: id }).then(response => {
                this.form = response.data;
                this.open = true;
                this.title = "修改数据信息";
            });
        },
        submitForm: function() {
            this.$refs["form"].validate(valid => {
                if (valid) {
                    if (this.form.${pkColumn.smallColumnName} != undefined) {
                        update${className}(this.form).then(response => {
                            this.msgSuccess("修改成功");
                            this.open = false;
                            this.getList();
                        });
                    } else {
                        add${className}(this.form).then(response => {
                            this.msgSuccess("新增成功");
                            this.open = false;
                            this.getList();
                        });
                    }
                }
            });
        },
        handleDelete(row) {
            const ids = row.${pkColumn.smallColumnName} || this.ids
            this.$confirm('是否确认删除列表编号为"' + ids + '"的数据项?', "警告", {
                    confirmButtonText: "确定",
                    cancelButtonText: "取消",
                    type: "warning"
                }).then(function() {
                    return del${className}(ids);
                }).then(() => {
                    this.getList();
                    this.msgSuccess("删除成功");
            })
        },
        handleExport() {
            const queryParams = this.queryParams;
            this.$confirm('是否确认导出所有列表数据项?', "警告", {
                confirmButtonText: "确定",
                cancelButtonText: "取消",
                type: "warning"
            }).then(function() {
                return export${className}(queryParams);
            }).then(response => {
                this.download(response.msg);
            })
        }
    }
};
</script>
