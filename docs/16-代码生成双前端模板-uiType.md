# 代码生成：Element / Ant Design Vue × 动态 / 扁平查询模板

## 概述

代码生成 Vue 页由 **`uiType` × `queryMode`** 共同决定模板：

| uiType | queryMode | Vue 模板 | 说明 |
|--------|-----------|----------|------|
| `element`（默认） | `dynamic`（默认） | `vue.ftl` | Element + DynamicQueryForm |
| `element` | `flat` | `vue-flat.ftl` | Element + 顶部扁平筛选项 |
| `antd` | `dynamic` | `vue-antd.ftl` | Ant Design Vue + 内联动态条件 |
| `antd` | `flat` | `vue-antd-flat.ftl` | Ant Design Vue + 扁平筛选项 |

别名：`ant` / `ant-design-vue` / `antdv` → `antd`；`simple` / `form` → `flat`。

## API

| 接口 | 参数 |
|------|------|
| `GET /generator/download/{tableName}` | `uiType`、`queryMode` |
| `GET /generator/previewCodeByGenTable/{tableId}` | 同上 |
| `GET /generator/downloadByGenTable/{tableIds}` | 同上 |

实现：`GenCodeController` → `setUiType` / `setQueryMode`（ThreadLocal）→ `resolveVueTemplateName()` → `clearGenerateContext()`。

## 字段类型适配（queryValueType）

生成列通过 `TableColumnInfo.getQueryValueType()`（`GenUtil.resolveQueryValueType`）得到：

`text` | `textarea` | `number` | `select` | `date` | `datetime` | `switch`

- **动态查询**：按类型裁剪运算符；`isNull` / `isNotNull` 时值控件禁用；number/date/datetime/select 用对应控件。
- **扁平查询**：顶部表单按类型渲染 `el-input-number` / `el-date-picker` / `a-input-number` / `a-date-picker` 等；并带 BaseEntity 的 **创建时间区间**（`beginTime`/`endTime`）与 **searchValue**。
- **searchValue**：生成 `service-impl.ftl` 会 `applyKeywordColumns`（varchar/char 列，排除 password / create_by / update_by）；前端传关键词即可多列 OR 模糊。

## 列表请求（方案 A）

- `POST /list`：body 传 `conditionsJson` **或** 扁平字段（互斥）；`pageNum`/`pageSize` 走 query。
- 可选请求头 `X-GP-Conditions-Json`。
- Controller 模板带 `@DataScope(deptAlias/userAlias=tableAlias)`；Mapper 列表仅别名版 `selectXxxList`。

详见 [17-动态条件查询.md](./17-动态条件查询.md)。

## 前端约定

- **geekplus-blog 管理端**：默认 `uiType=element` + `queryMode=dynamic`，工具页可切换四组合预览/下载。
- **Plus Admin**：可默认 `uiType=antd`。

## 相关文件

- `generator/template/vue/vue.ftl` / `vue-flat.ftl` / `vue-antd.ftl` / `vue-antd-flat.ftl`
- `generator/template/js/vue-js.ftl`（POST list）
- `generator/template/java/controller.ftl`（GET+POST list、`@DataScope`）
- `CodeGenerateByTemplate.java`、`GenCodeController.java`
