# AI 对话历史：Redis 与 DB 分工

> 对应源码：`GeminiChatService`、`ChatAIController`（`/ai/*`）、`ChatHistoryRedisTtl`、`ChatHistoryPersistence`、表 `chat_ai_log`  
> 前端：`geekplus-blog-frontend/src/views/chatbot/index.vue`

---

## 1. 结论（先读）

| 角色 | 真相源 | Redis | 说明 |
|------|--------|-------|------|
| **登录用户** | **MySQL `chat_ai_log`** | 热缓存（**5 天** TTL，可回填） | 每次问答 **双写**：Redis 列表 + `saveChatLogIfMember` → DB |
| **guest 访客** | **MySQL `chat_ai_log`**（审计） | **24h** TTL 热缓存 | 双写 Redis + DB；Redis 过期后可 hydrate；后台可按 `username=guest` / IP 查 |

**不要**把 Redis 当登录用户的长期存储；**要**把 DB 当唯一真相源，Redis 只加速「当前会话 / 多轮上下文 / 侧边栏列表」。

---

## 2. TTL 统一说明（2026-08 已落地）

### 2.1 改造前的问题

同一 Redis key 在不同代码路径上会被设成**不同 TTL**，取决于「最后一次操作」是哪条路径：

| 路径 | 改造前 TTL | 说明 |
|------|-----------|------|
| 流式 `/ai/chat/stream` | **24h** | 每轮对话 `expire` |
| 非流式 Gemini `getGeminiContent` | **24h** | 同上 |
| DB 回填 `hydrateSessionFromDb` / `reGetRedisChat` / `queryChatListWithMutex` | **5d** | 从 MySQL 预热 Redis |
| 旧版 `ChatGPTService` | **8h** | 遗留 OpenAI 路径 |

**后果**：活跃用户每聊一轮 TTL 被刷成 24h；若 24h 内没再聊，Redis 过期但 DB 仍有数据（现已支持 hydrate 恢复）。更糟的是：若用户刚被 DB 回填成 5d，再发一条消息又变 24h——**同一 key 双标准**，运维和排障都不直观。

### 2.2 改造后（`ChatHistoryRedisTtl`）

统一入口：`ChatHistoryRedisTtl.refreshSessionExpire(redis, redisKey, username)`

| 用户类型 | TTL | 常量 |
|----------|-----|------|
| guest | **24 小时** | `GUEST_SESSION_TTL` |
| 登录用户 | **5 天** | `MEMBER_SESSION_TTL` |

**所有**写入 Redis 的路径（流式、非流式、DB hydrate、mutex 预热、旧 ChatGPT 路径）均调用上述方法，不再散落硬编码 `24h` / `5d` / `8h`。

登录用户：活跃聊天与 DB 回填 TTL **一致为 5d**；超过 5d 未访问 Redis 过期，仍可从 DB hydrate。

---

## 3. 当前实现

### 3.1 写入

1. 生成 `redisKey = username:chatRecordId`（guest 用设备指纹 MD5）  
2. 流/非流结束后：`rightPush` 用户消息 + AI 回复到 Redis List  
3. `ChatHistoryRedisTtl.refreshSessionExpire(...)`  
4. **`ChatHistoryPersistence.saveChatLogIfMember(...)`** — guest / 登录用户均 `insertChatAILog`

### 3.2 读取

| 接口 | 行为 |
|------|------|
| `GET /ai/getAllHistoryMessage` | 登录：`queryChatListWithMutex` — Redis 无 key 则从 DB 拉 recordId 并回填（5d） |
| `GET /ai/getOneHistoryMessage?historyMsgKey=` | Redis 有则 range；无则 `hydrateSessionFromDb`（登录用户从 DB 回填，5d） |
| `GET /ai/getHistoryMessage` | guest 当前设备指纹 key；登录用户需带 `historyId` |
| `GET /ai/reGetRedisChat` | 手动全量从 DB 重建 Redis（5d） |

### 3.3 删除

| 用户 | `deleteHistoryMessage` |
|------|------------------------|
| guest / 登录 | 删 DB `chat_record_id` 对应行 + 删 Redis key |

---

## 4. 推荐策略（产品级）

### 4.1 登录用户

```
写：  用户发消息 → AI 回复完成
      ├─ INSERT chat_ai_log（真相）
      └─ APPEND redis list + refreshSessionExpire（5d）

读单会话： Redis range → miss → DB hydrate → 5d

删：     DELETE db + DEL redis
```

### 4.2 guest 访客

- Redis TTL **24h**（设备指纹 key：`guest:{md5}`）  
- **同时写 DB**，便于后台「AI聊天日志」按 `username=guest`、IP 查询  
- Redis 过期后 `getOneHistoryMessage` 可从 DB hydrate  

### 4.3 多轮上下文（发给模型）

- 前端 `isHistory` → 请求体 `historyChatData`  
- 建议上限：**最近 20 条或 8k tokens**

---

## 5. 源码索引

| 类 | 职责 |
|----|------|
| `ChatHistoryRedisTtl` | guest/member TTL、`isGuestUser`、`refreshSessionExpire` |
| `ChatHistoryPersistence` | 统一 `saveChatLogIfMember` → insert（含 guest） |
| `GeminiChatService` | hydrate、mutex 预热、删除、非流式 Gemini |
| `ChatAIController` | 流式 SSE 保存 |
| `ChatGPTService` | 遗留 OpenAI 路径（已对齐 TTL + guest skip） |

---

## 6. 后续可优化（未做）

1. **会话元数据表**：`chat_session(id, user, title, updated_at)`，侧边栏标题不依赖 Redis 最后一条 JSON  
2. **前端**：打开会话若空列表，一般不必手动 `reGetRedisChat`（DB hydrate 已覆盖）

---

## 7. 前端契约

- 侧边栏项：`historyMsgKey` = `username:chatRecordId`  
- 新会话：后端 SSE `done.recordId` → 前端写入 `chatDataPrompt.historyId`  
- 删会话：`deleteHistoryMessage`  
- 文档：`GeekPlus-Blog-API/docs/18-AI对话历史Redis与DB策略.md`
