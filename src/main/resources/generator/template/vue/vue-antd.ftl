<template>
  <div class="app-container">
    <a-card :bordered="false">
      <!-- 动态查询条件 -->
      <div v-show="showSearch" class="table-page-search-wrapper">
        <a-form-model layout="inline" class="dynamic-query-form">
          <div
            v-for="(cond, index) in conditions"
            :key="cond._key"
            class="dynamic-query-row"
          >
            <a-form-model-item :label="index === 0 ? '查询字段' : ''">
              <a-select
                v-model="cond.field"
                allow-clear
                placeholder="选择字段"
                style="width: 160px"
              >
                <a-select-option
                  v-for="opt in fieldOptions"
                  :key="opt.value"
                  :value="opt.value"
                >{{ opt.label }}</a-select-option>
              </a-select>
            </a-form-model-item>
            <a-form-model-item :label="index === 0 ? '运算符' : ''">
              <a-select
                v-model="cond.op"
                placeholder="运算符"
                style="width: 130px"
                @change="onOperatorChange(cond)"
              >
                <a-select-option
                  v-for="op in operatorOptions"
                  :key="op.value"
                  :value="op.value"
                >{{ queryConfig.showOperatorLabel ? op.label : op.symbol }}</a-select-option>
              </a-select>
            </a-form-model-item>
            <a-form-model-item :label="index === 0 ? '值' : ''">
              <a-input
                v-model="cond.value"
                allow-clear
                placeholder="请输入值"
                style="width: 200px"
                :disabled="isNullOp(cond.op)"
                @pressEnter="handleQuery"
              />
            </a-form-model-item>
            <a-form-model-item>
              <a-button
                type="danger"
                ghost
                icon="minus"
                :disabled="conditions.length <= 1"
                @click="removeCondition(index)"
              >删除条件</a-button>
            </a-form-model-item>
          </div>
          <div class="dynamic-query-actions">
            <a-button
              type="dashed"
              icon="plus"
              :disabled="conditions.length >= queryConfig.maxConditions"
              @click="addCondition"
            >增加条件</a-button>
            <a-button type="primary" icon="search" style="margin-left: 8px" @click="handleQuery">搜索</a-button>
            <a-button icon="reload" style="margin-left: 8px" @click="resetQuery">重置</a-button>
          </div>
        </a-form-model>

        <a-collapse :bordered="false" class="query-config-collapse">
          <a-collapse-panel key="config" header="查询配置">
            <a-form-model layout="inline">
              <a-form-model-item label="最大条件数">
                <a-input-number
                  v-model="queryConfig.maxConditions"
                  :min="1"
                  :max="20"
                  style="width: 100px"
                />
              </a-form-model-item>
              <a-form-model-item label="显示运算符文案">
                <a-switch v-model="queryConfig.showOperatorLabel" />
              </a-form-model-item>
            </a-form-model>
          </a-collapse-panel>
        </a-collapse>
      </div>

      <div class="table-operator">
        <a-button type="primary" icon="plus" @click="handleAdd">新增</a-button>
        <a-button
          icon="edit"
          style="margin-left: 8px"
          :disabled="single"
          @click="handleUpdate"
        >修改</a-button>
        <a-button
          type="danger"
          icon="delete"
          style="margin-left: 8px"
          :disabled="multiple"
          @click="handleDelete"
        >删除</a-button>
        <a-button
          icon="download"
          style="margin-left: 8px"
          @click="handleExport"
        >导出</a-button>
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

      <pagination
        v-show="total > 0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      />
    </a-card>

    <!-- 添加或修改数据对话框 -->
    <a-modal
      :title="title"
      :visible="open"
      :confirm-loading="submitLoading"
      width="780px"
      destroy-on-close
      @ok="submitForm"
      @cancel="cancel"
    >
      <a-form-model
        ref="form"
        :model="form"
        :rules="rules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
      >
        <a-row :gutter="16">
<#if allColumn?exists>
<#list allColumn as column>
<#if (column.columnDataType=='varchar'||column.columnDataType=='char') && column.isPk!='1' >
          <a-col :span="12">
            <a-form-model-item label="${column.columnComment}" prop="${column.smallColumnName}">
              <a-input v-model="form.${column.smallColumnName}" placeholder="请输入${column.columnComment}" allow-clear />
            </a-form-model-item>
          </a-col>
<#elseif (column.columnDataType=='tinyint'||column.columnDataType=='smallint') && column.isPk!='1' >
          <a-col :span="12">
            <a-form-model-item label="${column.columnComment}" prop="${column.smallColumnName}">
              <a-select v-model="form.${column.smallColumnName}" placeholder="请选择" allow-clear style="width: 100%">
                <a-select-option value="1">Label1</a-select-option>
                <a-select-option value="2">Label2</a-select-option>
              </a-select>
            </a-form-model-item>
          </a-col>
<#elseif (column.columnDataType=='int'||column.columnDataType=='bigint') && column.isPk!='1' >
          <a-col :span="12">
            <a-form-model-item label="${column.columnComment}" prop="${column.smallColumnName}">
              <a-input-number
                v-model="form.${column.smallColumnName}"
                :min="1"
                :max="999"
                placeholder="请输入${column.columnComment}"
                style="width: 100%"
              />
            </a-form-model-item>
          </a-col>
<#elseif (column.columnDataType=='text'||column.columnDataType=='tinytext'||column.columnDataType=='bigtext'||column.columnDataType=='longtext') && column.isPk!='1' >
          <a-col :span="24">
            <a-form-model-item
              label="${column.columnComment}"
              prop="${column.smallColumnName}"
              :label-col="{ span: 3 }"
              :wrapper-col="{ span: 20 }"
            >
              <a-textarea v-model="form.${column.smallColumnName}" :rows="3" placeholder="请输入内容" />
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
import { dateFormat } from "@/utils/gputil";
import Pagination from "@/components/Pagination";
import RightToolbar from "@/components/RightToolbar";

let conditionKey = 0;

function createEmptyCondition() {
  return {
    _key: ++conditionKey,
    field: undefined,
    op: "eq",
    value: undefined
  };
}

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
      queryParams: {
        pageNum: 1,
        pageSize: 10
      },
      // 动态查询条件（默认 2 行）
      conditions: [createEmptyCondition(), createEmptyCondition()],
      queryConfig: {
        maxConditions: 8,
        showOperatorLabel: true
      },
      fieldOptions: [
<#if allColumn?exists>
<#list allColumn as column>
<#if column.isPk!='1' >
        { value: "${column.smallColumnName}", label: "${column.columnComment}" },
</#if>
</#list>
</#if>
      ],
      operatorOptions: [
        { value: "eq", symbol: "=", label: "等于(=)" },
        { value: "ne", symbol: "≠", label: "不等于(≠)" },
        { value: "gt", symbol: ">", label: "大于(>)" },
        { value: "ge", symbol: "≥", label: "大于等于(≥)" },
        { value: "lt", symbol: "<", label: "小于(<)" },
        { value: "le", symbol: "≤", label: "小于等于(≤)" },
        { value: "like", symbol: "包含", label: "包含(like)" },
        { value: "notLike", symbol: "不包含", label: "不包含(notLike)" },
        { value: "isNull", symbol: "为空", label: "为空(isNull)" },
        { value: "isNotNull", symbol: "不为空", label: "不为空(isNotNull)" }
      ],
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
      },
      columns: [
<#if allColumn?exists>
<#list allColumn as column>
<#if column.javaType == 'Date'>
        {
          title: "${column.columnComment}",
          dataIndex: "${column.smallColumnName}",
          width: 170,
          ellipsis: true,
          customRender: text => dateFormat(text)
        },
<#else>
        {
          title: "${column.columnComment}",
          dataIndex: "${column.smallColumnName}",
          ellipsis: true
        },
</#if>
</#list>
</#if>
        {
          title: "操作",
          dataIndex: "action",
          width: 140,
          fixed: "right",
          scopedSlots: { customRender: "action" }
        }
      ]
    };
  },
  created() {
    this.getList();
  },
  methods: {
    isNullOp(op) {
      return op === "isNull" || op === "isNotNull";
    },
    onOperatorChange(cond) {
      if (this.isNullOp(cond.op)) {
        cond.value = undefined;
      }
    },
    addCondition() {
      if (this.conditions.length >= this.queryConfig.maxConditions) {
        this.$message.warning("最多添加 " + this.queryConfig.maxConditions + " 个条件");
        return;
      }
      this.conditions.push(createEmptyCondition());
    },
    removeCondition(index) {
      if (this.conditions.length <= 1) {
        return;
      }
      this.conditions.splice(index, 1);
    },
    /**
     * 合并分页与动态条件：
     * - pageNum / pageSize
     * - eq / like 时同步写入 queryParams[field]（兼容旧扁平筛选）
     * - 始终附带 conditionsJson 字符串（GET 友好，后端 DynamicQueryHelper 解析）
     */
    buildQueryParams() {
      const params = {
        pageNum: this.queryParams.pageNum,
        pageSize: this.queryParams.pageSize
      };
      const dynamicConditions = [];
      (this.conditions || []).forEach(cond => {
        if (!cond || !cond.field || !cond.op) {
          return;
        }
        if (this.isNullOp(cond.op)) {
          dynamicConditions.push({
            field: cond.field,
            op: cond.op,
            value: null
          });
          return;
        }
        if (cond.value === undefined || cond.value === null || cond.value === "") {
          return;
        }
        dynamicConditions.push({
          field: cond.field,
          op: cond.op,
          value: cond.value
        });
        if (cond.op === "eq" || cond.op === "like") {
          params[cond.field] = cond.value;
        }
      });
      if (dynamicConditions.length) {
        params.conditionsJson = JSON.stringify(dynamicConditions);
      }
      return params;
    },
    getList() {
      this.loading = true;
      const params = this.buildQueryParams();
      list${className}(params).then(response => {
        const data = response.data || response;
        this.list = data.rows || data.list || (Array.isArray(data) ? data : []);
        this.total = data.total || 0;
        this.loading = false;
      }).catch(() => {
        this.loading = false;
      });
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
      this.$nextTick(() => {
        this.$refs.form && this.$refs.form.clearValidate();
      });
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    resetQuery() {
      this.conditions = [createEmptyCondition(), createEmptyCondition()];
      this.queryParams = {
        pageNum: 1,
        pageSize: this.queryParams.pageSize || 10
      };
      this.handleQuery();
    },
    handleSelectionChange(selectedRowKeys) {
      this.selectedRowKeys = selectedRowKeys;
      this.ids = selectedRowKeys;
      this.single = selectedRowKeys.length !== 1;
      this.multiple = !selectedRowKeys.length;
    },
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加数据信息";
    },
    handleUpdate(row) {
      this.reset();
      const id = (row && row.${pkColumn.smallColumnName}) || this.ids[0];
      get${className}({ ${pkColumn.smallColumnName}: id }).then(response => {
        this.form = Object.assign({}, this.form, response.data || response);
        this.open = true;
        this.title = "修改数据信息";
      });
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) {
          return;
        }
        this.submitLoading = true;
        const req = this.form.${pkColumn.smallColumnName} != undefined
          ? update${className}(this.form)
          : add${className}(this.form);
        req.then(() => {
          this.$message.success(this.form.${pkColumn.smallColumnName} != undefined ? "修改成功" : "新增成功");
          this.open = false;
          this.getList();
        }).finally(() => {
          this.submitLoading = false;
        });
      });
    },
    handleDelete(row) {
      const ids = (row && row.${pkColumn.smallColumnName}) || this.ids;
      const that = this;
      this.$confirm({
        title: "警告",
        content: '是否确认删除列表编号为"' + ids + '"的数据项?',
        okType: "danger",
        onOk() {
          return del${className}(ids).then(() => {
            that.getList();
            that.selectedRowKeys = [];
            that.ids = [];
            that.single = true;
            that.multiple = true;
            that.$message.success("删除成功");
          });
        }
      });
    },
    handleExport() {
      const queryParams = this.buildQueryParams();
      const that = this;
      this.$confirm({
        title: "警告",
        content: "是否确认导出所有列表数据项?",
        onOk() {
          return export${className}(queryParams).then(response => {
            const msg = (response && response.msg) || (response && response.data && response.data.msg);
            if (that.download && msg) {
              that.download(msg);
            } else {
              that.$message.success("导出成功");
            }
          });
        }
      });
    }
  }
};
</script>

<style scoped>
.dynamic-query-form {
  margin-bottom: 8px;
}
.dynamic-query-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  margin-bottom: 4px;
}
.dynamic-query-actions {
  margin: 8px 0 12px;
}
.query-config-collapse {
  margin-bottom: 12px;
  background: transparent;
}
.query-config-collapse >>> .ant-collapse-header {
  padding-left: 0 !important;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}
.query-config-collapse >>> .ant-collapse-content-box {
  padding: 8px 0 0;
}
.table-operator {
  margin-bottom: 16px;
}
</style>
