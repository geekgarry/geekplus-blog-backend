# 代码生成：Element / Ant Design Vue 双前端模板

## 概述

代码生成在原有 **Element UI** 页面模板（`vue.ftl`）基础上，增加 **Ant Design Vue** 模板（`vue-antd.ftl`），通过请求参数 `uiType` 切换，互为互补。

| uiType | Vue 模板 | 典型消费端 |
|--------|----------|------------|
| `element`（默认） | `generator/template/vue/vue.ftl` | geekplus-blog-frontend |
| `antd` | `generator/template/vue/vue-antd.ftl` | Plus Admin（Ant Design Vue Pro） |

别名：`ant` / `ant-design-vue` / `antdv` → `antd`。

## API

| 接口 | 参数 |
|------|------|
| `GET /generator/download/{tableName}` | `uiType` query，默认 `element` |
| `GET /generator/previewCodeByGenTable/{tableId}` | `uiType` |
| `GET /generator/downloadByGenTable/{tableIds}` | `uiType` |

实现：`GenCodeController` → `CodeGenerateByTemplate.setUiType`（ThreadLocal）→ `resolveVueTemplateName()`。

## Ant 模板特性（`vue-antd.ftl`）

- Ant Design Vue 1.x 组件（`a-form-model` / `a-table` / `a-modal` / `pagination`）
- **动态条件查询**（默认 2 行，可增删）：
  - 字段下拉（非主键列）
  - 条件：`eq` / `ne` / `gt` / `ge` / `lt` / `le` / `like` / `notLike` / `isNull` / `isNotNull`
  - 值输入（为空/不为空时隐藏）
  - 「查询配置」：最大条件数、是否显示操作符文案
- `buildQueryParams()`：兼容扁平字段 + **`conditionsJson` 字符串**（JSON 数组，供 `DynamicQueryHelper` 解析）

详见 [17-动态条件查询.md](./17-动态条件查询.md)。`eq`/`like` 仍同步写入同名字段，保证仅扁平筛选的旧 Mapper 可用。

## 前端约定

- **Plus Admin**：代码生成页默认 `uiType=antd`
- **geekplus-blog 管理端**：默认 `uiType=element`，可切换为 antd 预览/下载

## 相关文件

- `src/main/resources/generator/template/vue/vue.ftl`
- `src/main/resources/generator/template/vue/vue-antd.ftl`
- `src/main/resources/generator/template/html/index.ftl`（Bootstrap 5 + jQuery AJAX，与 Vue 同形态的动态条件 CRUD 静态页）
- `CodeGenerateByTemplate.java`、`GenCodeController.java`

## Bootstrap HTML 模板（`html/index.ftl`）

与 Vue 列表页能力对齐的独立 HTML 页，便于非 SPA / 快速联调：

- **动态条件**：字段下拉 + 运算符 + 值，默认 2 行，可增删；可调最大条件数
- **请求**：jQuery AJAX → `GET list` / `GET/{id}` / `POST add|update` / `DELETE /{ids}` / `GET export`
- **参数**：`conditionsJson` 字符串 + `eq`/`like` 扁平字段兼容（同 DynamicQuery 约定）
- **样式**：Bootstrap 5 CDN；鉴权头尝试读取 Cookie/本地存储中的 `Plus-Token`
- 预览与 ZIP 包路径：`/html/{module}/{business}/index.html`
