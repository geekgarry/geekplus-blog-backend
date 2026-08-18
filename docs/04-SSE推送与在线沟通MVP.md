# SSE 推送 + 在线沟通（MVP）

与既有 `WebSocketServer` / 前端 `wesocket.js` **并存**。

**聊天实时通道：WebSocket（推荐）**；SSE 适合预警等单向推送。`RealtimePushBroker` 仍双通道下发，前端聊天入口确保 `websocket.Init(userId)`。

## 后端（GeekPlus-Blog-API）

| 路径 | 说明 |
|------|------|
| `GET /sse/subscribe/{clientId}?Plus-Token=` | SSE 订阅（预警/通知/信令） |
| `POST /sse/push/{clientId}` | 单推测试 |
| `GET /sse/online` | SSE 在线客户端 |
| `GET /realtime/onlineUsers` | WS+SSE 合并在线用户 |
| `POST /realtime/chat/send` | 文本聊天 |
| `GET /realtime/chat/history` | 内存历史（重启清空） |
| `POST /realtime/webrtc/signal` | WebRTC 信令 |

`RealtimePushBroker`：业务推送同时打 WS + SSE。`SystemNotifyController` 已改用 Broker。

`JwtFilter`：兼容 query `Plus-Token` / `token`（EventSource 无法自定义 Header）。

## 前端

| 文件 | 说明 |
|------|------|
| `src/utils/wesocket.js` | **聊天主通道**：动态 WS URL（`VUE_APP_WS_URL` 或同源 + BASE_API） |
| `src/utils/ssePush.js` | SSE 客户端（预警可选）；派发 `onmessageSSE` |
| `src/components/RealtimeChat/` | 可移植聊天壳：桌面左右栏 / 移动列表→会话；头像昵称、未读、乐观发送 |
| `src/views/im/index.vue` + 路由 `/im` | 独立全页（弹窗「新页面」/ 移动端直达） |
| 管理端 Navbar / Layout FAB | 入口（登录后 `websocket.Init(userId)`） |
| 博客双布局 | 复用 `RealtimeChatEntry` |
| 设置「管理端水印」 | 仅 admin Layout |

**实时约定：** 订阅与聊天 `clientId` 统一为 **userId 字符串**；发消息仍走 HTTP，收消息靠 WS 推送（Broker 同时打 SSE）。

生产环境建议 **优先同源**：

```bash
# .env.production
VUE_APP_WS_URL=
VUE_APP_WS_URL_FALLBACK=wss://api.example.com:port/websocket
```

前端会先连 `wss://当前域名/pro-api/websocket/{userId}?Plus-Token=`，失败再试 FALLBACK，再失败降级 SSE。  
**安卓 Chrome 对跨域/证书链更严**，直连 API 域名易失败，表现为「对方能看到我，我看不到对方」（列表不刷新或本端未真正在线通道）。

Nginx（`www` → API）需支持 Upgrade，例如：

```nginx
location /pro-api/ {
    proxy_pass https://api.example.com:port/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header Plus-Token $http_plus_token;
    proxy_read_timeout 3600s;
}
```

## 升级方向

1. 消息落库 + 离线推送  
2. Netty / Redis Pub-Sub 扩展推送  
3. TURN 服务支撑复杂网络视频  
4. （可选）加好友 / 会话列表持久化  

## 媒体消息与通话（2026-08）

- 聊天支持 **图片 ≤5MB / 视频 ≤20MB**（先 `/common/upload` 再 `msgType=image|video`）；更大文件引导「文件中转」。  
- WebRTC：`hangup/reject/error` **双边收尾**（Broker 回推自己 + 前端 `endCallLocal` 清画面）；ICE/SDP 纯 JSON；信令失败不抛 500。  
- WS `Connection reset` 降为 warn，推送用 async + 吞异常，避免拖垮 HTTP 信令。  
- 详见 [06 文档](./06-实时推送方案选型与WebSocket改造.md) 增补节。

