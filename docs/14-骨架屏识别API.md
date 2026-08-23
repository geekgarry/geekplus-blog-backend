# 14 · 骨架屏识别 API

> 对应前端：`/tool/skeletonStudio`（P1 粘贴/文件纯前端；P2/P3 走本仓）

## 契约

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/skeleton/fetch-url` | 代理抓取公开页 HTML（Jsoup，不执行 JS） |
| POST | `/api/skeleton/from-image` | 截图 base64 → AI → Schema JSON 文本 |

### fetch-url

请求：`{ "url": "https://..." }`

响应 `data`：

```json
{
  "url": "...",
  "title": "...",
  "html": "<body 内清理后片段>",
  "status": 200
}
```

限制：仅 `http/https`；拒绝 localhost / 常见私网段；超时约 15s；body 截断约 80 万字符。SPA 需前端 JS 渲染的结构会漏，属预期。

### from-image

请求：

```json
{
  "mediaData": "<base64>",
  "mediaMimeType": "image/jpeg",
  "provider": null,
  "model": null,
  "sourceId": null
}
```

响应 `data`：模型返回的 **字符串**（应为 `{ version, name, canvas, nodes }` JSON）。前端用与简历相同的 `parseAiJson` 解析。

需站点已配置 AI 源（`AiService.chat` + 多模态）。

## 鉴权

与 `/api/ppt`、`/api/resume/ai` 一致：走默认登录态（非 anon）。

## 前端配合

- P1：`src/components/skeleton/skeletonImport.js`（DOMParser）
- API 封装：`src/api/skeleton.js`
