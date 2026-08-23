# 11 · 简历工具 AI 扩展（解析 / 分析 / 岗位搜索 / PPT）

> 对应前端：`geekplus-blog-frontend` 的 `/tool/resumeGenerator`  
> 对照产品：简历制作已具备的分析 / PPT，在本博客前端补齐，并新增岗位搜索。

## 背景

历史上「上传 PDF/DOC → AI 生成」前端把 `File` 直接塞进 JSON `mediaData`。axios 序列化后附件变成 `{}`，模型只看到空 schema，于是返回：

```json
{ "code": 200, "msg": "{\"basics\":{\"name\":\"\",...}, \"work\":[] }" }
```

（另：`Result.success(String)` 重载会把正文放进 `msg`；已改为 `Result.success((Object) text)` 放入 `data`。）

## 契约

### `POST /api/resume/ai/generate`

- Body：`ChatPrompt`（`chatMsg` + 可选 `mediaData` base64/`dataURL`、`mediaMimeType`、`mediaFileName`）
- 前端须先本地抽字（pdfjs / mammoth），再把正文写入 `chatMsg`，并把原文件 base64 放进 `mediaData`（Gemini 多模态兜底；ChatGPT 仅吃文本）

### `POST /api/resume/ai/analyze`

```json
{
  "type": "job | match | generate",
  "text": "JD 文本",
  "fileData": "可选 dataURL/base64",
  "mimeType": "image/png | application/pdf",
  "resumeData": { "...": "match/generate 必填" }
}
```

返回：`{ "result": "Markdown 或 JSON 字符串", "type": "..." }`

### `POST /api/resume/ai/job-search`

```json
{
  "keyword": "前端",
  "industry": "互联网",
  "city": "上海",
  "experience": "3-5年",
  "resumeData": { "jobIntention": { "targetJob": "...", "targetCity": "..." } }
}
```

返回：

```json
{
  "platformLinks": [{ "name": "BOSS直聘", "url": "...", "hint": "..." }],
  "insight": "市场洞察 Markdown",
  "jobs": [{ "title": "", "company": "", "city": "", "salary": "", "summary": "", "url": "" }],
  "keyword": "",
  "city": "",
  "industry": ""
}
```

说明：各大招聘站无稳定开放检索 API，本接口提供**平台深链 + AI 市场洞察/推荐参考**；实时列表请点平台链接查看。

### `PUT /api/resume/{id}/title?userId=`

```json
{ "title": "前端校招版" }
```

仅改显示名称，不碰 `data_json`。返回更新后的简历记录。

### `POST /api/resume/ai/generate-ppt`

```json
{ "resumeData": {}, "title": "可选", "prompt": "可选附加要求" }
```

返回：`{ "title": "", "slides": [{ "title": "", "content": "", "backgroundColor": "", "textColor": "" }] }`

## 前端模块

| 页签 | 组件 |
|------|------|
| 编辑简历 | `ResumeFormEditor` + `ResumePreview` |
| 岗位分析 | `ResumeAnalysis` |
| 岗位搜索 | `ResumeJobSearch` |
| PPT 制作 | `ResumePPTMaker` |

附件本地解析：`src/utils/resumeFileParse.js`
