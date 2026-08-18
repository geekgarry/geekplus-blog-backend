# 中后台实时推送选型 + WebSocket 改造说明

> 适用：GeekPlus 博客中后台预警、在线人数、IM MVP、WebRTC 信令。  
> 对照：常见博客与中后台产品的推送分层思路（非照搬其专有协议）。

---

## 1. 结论先说

| 问题 | 结论 |
|------|------|
| 旧 `WebSocketServer`（在线人数 / 心跳 / 推送）能不能做 IM？ | **可以**。同一条长连接用 JSON `type` 区分业务即可，不必再开一套端点。 |
| 这次 `wss://…/websocket/1` 失败主因？ | **握手被 Shiro `jwt` 拦住**（浏览器 WS 没带 Token）+ **`subprotocols=protocol` 与前端不一致**。 |
| 中后台默认推送怎么选？ | **管理端预警 / 未读：SSE 或 WS 皆可；IM / 信令：WebSocket。** 本站统一：**业务 HTTP 写入 + Broker 双通道下发（WS 主、SSE 备）**。 |

---

## 2. 成熟产品（含博客站）常见推送分层

博客网站等各类中后台并不「只用一种通道」，而是按场景拆：

```text
┌─────────────────────────────────────────────────────────┐
│ 业务层：发评论 / 发私信 / 发系统通知 / 改工单状态         │
│         → 写库（或消息队列）→ 得到 messageId             │
└───────────────────────────┬─────────────────────────────┘
                            │
              ┌─────────────▼─────────────┐
              │ 推送网关 / Broker          │
              │ （在线推 + 离线存）         │
              └──────┬──────────┬─────────┘
                     │          │
           ┌─────────▼──┐   ┌───▼──────────┐
           │ 在线通道    │   │ 离线 / 触达   │
           │ WS / SSE   │   │ DB 未读列表   │
           │            │   │ App Push/邮件 │
           └────────────┘   └──────────────┘
```

| 场景 | 常见做法 | 本站对应 |
|------|----------|----------|
| 系统公告、审批提醒、登录异地 | SSE 或 WS 推一条；列表仍 HTTP 拉 | `RealtimePushBroker` + 通知接口 |
| 站内信 / 私信 | **先落库**，在线再推；打开会话拉历史 | MVP 暂内存 HISTORY，下一步落库 |
| 在线人数、在线列表 | WS 连接池 key=userId | `WebSocketServer` + `/realtime/onlineUsers` |
| 音视频 | 信令走 WS/SSE，媒体 P2P/TURN | `/realtime/webrtc/signal` |
| 评论互动「有人回复了你」 | 写库 + 未读数 + 可选推送 | 可后续接 Broker |

**要点：** 即时通信 ≠「消息只活在 WebSocket 里」。成熟做法是 **HTTP（或 MQ）负责写入与权限，长连接只负责「有新货」**。你们当前 IM 已是「HTTP send + 推送」，方向正确。

---

## 3. 中后台怎么选技术方案

| 技术 | 优点 | 缺点 | 适合 |
|------|------|------|------|
| **短轮询** | 实现简单 | 延迟高、浪费 | 极低频、内网临时 |
| **长轮询** | 兼容旧环境 | 连接占用、实现绕 | 老系统过渡 |
| **SSE** | 一单向、自带重连、走 HTTP、实现轻 | 浏览器端难自定义 Header；反向代理易缓冲；仅服务端→客户端 | **预警、未读角标、进度条** |
| **WebSocket** | 双向、低延迟、适合聊天/信令 | 要处理鉴权、心跳、断线、多端；反代要 Upgrade | **IM、协同、WebRTC 信令** |
| **STOMP/SockJS** | 有订阅主题模型 | 偏重，与 JSR-356 原生端点两套 | 你们已有 `/chatAIWS` 给 AI 聊天，**不要和业务推送混用** |
| **第三方（个推/极光/企业微信）** | App/离线触达强 | 成本、依赖 | App 端再考虑 |

### 本站推荐组合（保持可演进）

```text
管理端登录后
  ├─ WebSocket  /websocket/{userId}?Plus-Token=…   ← 主通道（聊天/预警/在线）
  └─ （可选）SSE /sse/subscribe/{userId}?Plus-Token= ← 轻量备通道或纯通知页

发消息 / 发预警
  └─ HTTP API → RealtimePushBroker.pushToUser
        ├─ WebSocketServer.sendInfo
        └─ SsePushHub.sendTo
```

- **不要**为 IM 再新建第二个 JSR-356 端点；复用 `WebSocketServer`。  
- **不要**用 STOMP `/chatAIWS` 扛业务推送（协议与生命周期不同）。  
- 多实例部署时再上 **Redis Pub/Sub** 把 Broker 从「本机 Map」升级为「集群广播」（文档升级项）。

---

## 4. 本次故障与改造

### 4.1 根因

1. **Shiro**：`/websocket/**` 的 `anon` 被注释，落入 `jwt`；浏览器 `new WebSocket(url)` **不能设 Header**，未带 Token → 握手 401，表现为 `WebSocket connection failed`。  
2. **Subprotocol**：服务端曾声明 `subprotocols = {"protocol"}`，前端未协商，部分环境下握手失败。  
3. （次要）旧前端曾写死 WS 地址；现已 `VUE_APP_WS_URL` + Token。

SSE 能通，是因为一开始就按 EventSource 限制做了 `?Plus-Token=`。

### 4.2 代码改动

| 位置 | 改动 |
|------|------|
| 前端 `wesocket.js` | URL 追加 `Plus-Token`；无 Token 不连；手动关闭不重连风暴 |
| 后端 `WebSocketServer` | 去掉强制 `subprotocols`；心跳安全处理；上行业务消息忽略（改走 HTTP+Broker） |
| `ShiroConfig` | 明确：**WS 继续走 jwt，靠 query Token**，禁止轻易改回全 anon |
| `JwtFilter` | 已支持 query `Plus-Token` / `token`（与 SSE 共用） |

### 4.3 连接契约（前后端必须一致）

```text
wss://{api-host}:{port}/websocket/{userId}?Plus-Token={jwt}

userId = 登录用户主键字符串（与 SSE clientId、chat fromClientId 一致）
```

生产 `.env.production`：

```bash
VUE_APP_WS_URL=wss://api.example.com:port/websocket
```

若前面有 Nginx，除证书外还需：

```nginx
proxy_http_version 1.1;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
proxy_set_header Host $host;
proxy_read_timeout 3600s;
```

### 4.4 复用旧 WebSocketServer 做 IM —— 职责划分

| 能力 | 谁负责 |
|------|--------|
| 建连 / 心跳 / 在线池 | `WebSocketServer` |
| 发聊天、落历史、权限 | `RealtimeChatController`（HTTP） |
| 推到在线用户 | `RealtimePushBroker` → WS（+ SSE） |
| 前端收包 | `onmessageWS` → `RealtimeChat` / 通知组件 |

**可以、也应该复用**；不要把「鉴权写库」塞进 `@OnMessage` 里手写转发（本次已收紧上行，避免绕过 API）。

---

## 5. 与 博客 类产品的差距（后续优先级）

| 能力 | 现状 | 建议优先级 |
|------|------|------------|
| 在线实时推送 | ✅ WS+SSE Broker | — |
| 握手鉴权 | ✅ query Token | — |
| 消息持久化 / 离线可拉 | ❌ 内存 HISTORY | P1 |
| 会话列表 / 未读持久化 | ⚠️ 前端未读仅会话内 | P1 |
| 多端 / 多实例 | ❌ 单机 Map | P2 Redis Pub/Sub |
| 加好友 | ❌ | P3 按需 |

---

## 6. 自测清单

1. 登录后控制台：`wss://…/websocket/{id}?Plus-Token=…` 状态 **101 Switching Protocols**。  
2. 无 Token 的 URL 应仍失败（预期）。  
3. 两用户互发：不点刷新会话也能出气泡。  
4. 管理端预警：WS 连着时能收到（或 SSE 备通道）。  
5. 心跳：约 30s 有 `heartBeat` 回包，连接不断。

---

## 8. 媒体消息与通话生命周期（增补）

### 媒体

| 类型 | 上限 | 流程 |
|------|------|------|
| 图片 | 5MB | `/common/upload` → `POST /realtime/chat/send`（`msgType=image`） |
| 视频 | 20MB | 同上（`msgType=video`） |
| 更大文件 | — | 引导站内「文件中转」（对象存储/分片），不走聊天直传 |

### 通话挂断

```text
任一方 hangup / reject / error / PC failed
  → signal（HTTP）→ Broker 推对方 +（hangup/reject/error 时）推自己
  → 两端 endCallLocal：停轨、清 video.srcObject、关 RTCPeerConnection
```

### 语音 / 视频控制

| 能力 | 说明 |
|------|------|
| 语音通话 | `callMode=audio`，仅麦克风；界面为头像舞台 |
| 视频通话 | `callMode=video` |
| 话筒 | 本地 `audioTrack.enabled` 开关 |
| 扬声器静音 | 远端 `audio/video.muted` |
| 关闭画面 | 本地 `videoTrack.enabled`（仅视频） |
| 翻转摄像头 | `enumerateDevices` 检测到 ≥2 路 `videoinput` 时显示，`replaceTrack` |

### 信令 500 / Connection reset

根因常见：ICE 推送时对端 WS 已 reset，同步 `sendText` 抛错冒泡到 HTTP。  
现：Broker/WS 推送吞异常 + async；`signal` 接口 try/catch 返回业务错误而非 500。

---

## 7. 相关代码索引

- `WebSocketServer.java` — JSR-356 端点（async 推送、Connection reset 降噪）  
- `RealtimePushBroker.java` — 双通道下发（失败不抛）  
- `RealtimeChatController.java` — IM HTTP + 媒体字段 + 安全信令  
- `RealtimeChat/index.vue` — 媒体气泡、通话双边收尾  
- `wesocket.js` / `ssePush.js` — 前端长连接  
