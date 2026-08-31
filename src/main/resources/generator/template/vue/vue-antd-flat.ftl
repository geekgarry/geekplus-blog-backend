<!-- 扁平条件查询（Ant Design Vue）：按 queryValueType 适配；含 BaseEntity 时间区间/searchValue；POST 只传扁平字段 -->
<template>
  <div class="app-container">
    <a-card :bordered="false">
      <div v-show="showSearch" class="table-page-search-wrapper">
        <a-form-model layout="inline" :model="queryParams">
<#if allColumn?exists>
<#list allColumn as column>
<#if column.isPk!='1' && column.smallColumnName != 'password' && column.smallColumnName != 'delFlag'>
          <a-form-model-item label="${column.columnComment}">
<#if column.queryValueType == 'number'>
            <a-input-number v-model="queryParams.${column.smallColumnName}" style="width: 180px" />
<#elseif column.queryValueType == 'date'>
            <a-date-picker v-model="queryParams.${column.smallColumnName}" value-format="YYYY-MM-DD" style="width: 180px" />
<#elseif column.queryValueType == 'datetime'>
            <a-date-picker show-time v-model="queryParams.${column.smallColumnName}" value-format="YYYY-MM-DD HH:mm:ss" style="width: 200px" />
<#elseif column.queryValueType == 'select' || column.queryValueType == 'switch'>
            <a-select v-model="queryParams.${column.smallColumnName}" allow-clear placeholder="请选择" style="width: 180px">
              <a-select-option :value="0">正常/是</a-select-option>
              <a-select-option :value="1">停用/否</a-select-option>
            </a-select>
<#else>
            <a-input v-model="queryParams.${column.smallColumnName}" allow-clear placeholder="请输入${column.columnComment}" style="width: 180px" @pressEnter="handleQuery" />
</#if>
          </a-form-model-item>
</#if>
</#list>
</#if>
          <a-form-model-item label="创建时间">
            <a-range-picker v-model="dateRange" value-format="YYYY-MM-DD" style="width: 240px" @change="onDateRangeChange" />
          </a-form-model-item>
          <a-form-model-item label="关键词">
            <a-input v-model="queryParams.searchValue" allow-clear placeholder="多字段关键词" style="width: 180px" @pressEnter="handleQuery" />
          </a-form-model-item>
          <a-form-model-item>
            <a-button type="primary" icon="search" @click="handleQuery">搜索</a-button>
            <a-button style="margin-left: 8px" icon="reload" @click="resetQuery">重置</a-button>
          </a-form-model-item>
        </a-form-model>
      </div>

      <div class="table-operator">
        <a-button type="primary" icon="plus" @click="handleAdd">新增</a-button>
        <a-button icon="edit" style="margin-left: 8px" :disabled="single" @click="handleUpdate">修改</a-button>
        <a-button type="danger" icon="delete" style="margin-left: 8px" :disabled="multiple" @click="handleDelete">删除</a-button>
        <a-button icon="download" style="margin-left: 8px" @click="handleExport">导出</a-button>
        <right-toolbar :show-search.sync="showSearch" @queryTable="getList" />
      </div>

      <a-table
        row-key="${pkColumn.smallColumnName}"
        size="middle"
        :loading="loading"
        :columns="columns"
        :data-source="list"
        :pagination="false"
        :row-selection="{ selectedRowKeys: selectedRowKeys, onChange: handleSelectionChange }"
        :scroll="{ x: 1100 }"
      >
        <span slot="action" slot-scope="text, record">
          <a @click="handleUpdate(record)">修改</a>
          <a-divider type="vertical" />
          <a-popconfirm title="确认删除？" @confirm="handleDelete(record)">
            <a style="color: #ff4d4f">删除</a>
          </a-popconfirm>
        </span>
      </a-table>

      <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
    </a-card>

    <a-modal :title="title" :visible="open" :confirm-loading="submitLoading" width="780px" destroy-on-close @ok="submitForm" @cancel="cancel">
      <a-form-model ref="form" :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-row :gutter="16">
<#if allColumn?exists>
<#list allColumn as column>
<#if (column.columnDataType=='varchar'||column.columnDataType=='char') && column.isPk!='1' >
          <a-col :span="12">
            <a-form-model-item label="${column.columnComment}" prop="${column.smallColumnName}">
              <a-input v-model="form.${column.smallColumnName}" placeholder="请输入${column.columnComment}" allow-clear />
            </a-form-model-item>
          </a-col>
<#elseif (column.columnDataType=='int'||column.columnDataType=='bigint'||column.columnDataType=='tinyint'||column.columnDataType=='smallint') && column.isPk!='1' >
          <a-col :span="12">
            <a-form-model-item label="${column.columnComment}" prop="${column.smallColumnName}">
              <a-input-number v-model="form.${column.smallColumnName}" style="width: 100%" />
            </a-form-model-item>
          </a-col>
<#elseif (column.columnDataType=='text'||column.columnDataType=='longtext') && column.isPk!='1' >
          <a-col :span="24">
            <a-form-model-item label="${column.columnComment}" prop="${column.smallColumnName}" :label-col="{ span: 3 }" :wrapper-col="{ span: 20 }">
              <a-textarea v-model="form.${column.smallColumnName}" :rows="3" />
            </a-form-model-item>
          </a-col>
</#if>
</#list>
</#if>
        </a-row>
      </a-form-model>
    </a-modal>
  </div>
</template>

<script>
import { list${className}, get${className}, del${className}, add${className}, update${className}, export${className} } from "@/api/${moduleName}/${jsMethodName}";
import Pagination from "@/components/Pagination";
import RightToolbar from "@/components/RightToolbar";

export default {
  name: "${componentName}",
  components: { Pagination, RightToolbar },
  data() {
    return {
      loading: true,
      submitLoading: false,
      selectedRowKeys: [],
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
<#if allColumn?exists>
<#list allColumn as column>
<#if column.isPk!='1' && column.smallColumnName != 'password' && column.smallColumnName != 'delFlag'>
        ${column.smallColumnName}: undefined,
</#if>
</#list>
</#if>
      },
      columns: [
<#if allColumn?exists>
<#list allColumn as column>
        { title: "${column.columnComment}", dataIndex: "${column.smallColumnName}", key: "${column.smallColumnName}", ellipsis: true },
</#list>
</#if>
        { title: "操作", key: "action", scopedSlots: { customRender: "action" }, fixed: "right", width: 140 }
      ],
      form: {},
      rules: {
<#if allColumn?exists>
<#list allColumn as column>
<#if column.javaType == 'String'>
        ${column.smallColumnName}: [{ required: true, message: "${column.columnComment}不能为空", trigger: "blur" }],
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
        const data = response.data || response;
        this.list = data.rows || data.list || [];
        this.total = data.total || 0;
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
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    resetQuery() {
      this.dateRange = [];
      Object.keys(this.queryParams).forEach((k) => {
        if (k === 'pageNum') this.queryParams[k] = 1;
        else if (k === 'pageSize') this.queryParams[k] = 10;
        else this.queryParams[k] = undefined;
      });
      this.handleQuery();
    },
    handleSelectionChange(selectedRowKeys, selectedRows) {
      this.selectedRowKeys = selectedRowKeys;
      this.ids = selectedRows.map(r => r.${pkColumn.smallColumnName});
      this.single = selectedRows.length !== 1;
      this.multiple = !selectedRows.length;
    },
    cancel() {
      this.open = false;
      this.reset();
    },
    reset() {
      this.form = {};
    },
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加数据信息";
    },
    handleUpdate(row) {
      this.reset();
      const id = (row && row.${pkColumn.smallColumnName}) || this.ids[0];
      get${className}(id).then(response => {
        this.form = (response.data || response) || {};
        this.open = true;
        this.title = "修改数据信息";
      });
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return;
        this.submitLoading = true;
        const req = this.form.${pkColumn.smallColumnName} != null
          ? update${className}(this.form)
          : add${className}(this.form);
        req.then(() => {
          this.$message.success(this.form.${pkColumn.smallColumnName} != null ? '修改成功' : '新增成功');
          this.open = false;
          this.getList();
        }).finally(() => { this.submitLoading = false; });
      });
    },
    handleDelete(row) {
      const ids = (row && row.${pkColumn.smallColumnName}) || this.ids;
      const doDel = () => del${className}(ids).then(() => {
        this.$message.success('删除成功');
        this.getList();
      });
      if (row) return doDel();
      this.$confirm({ title: '确认删除所选数据？', onOk: doDel });
    },
    handleExport() {
      export${className}(this.queryParams);
    }
  }
};
</script>
