# GeekPlus-Blog-API 技术文档

> 后端仓库文档
> 前端实践文（布局 / 路由 / request 等）在兄弟仓库：`geekplus-blog-frontend/docs/v2/`

本目录只维护 **API / 鉴权 / 缓存 / 全栈契约** 相关方案，与源码同仓，便于评审与落地。

**维护约定：** 后端代码有新增或优化时，须同步更新本目录对应文档（并视情况改本 README 索引），不要只改代码。

---

## 目录

| 序号 | 文件 | 一句话 |
|------|------|--------|
| **1** | [01-全栈技术架构与前后端协作.md](./01-全栈技术架构与前后端协作.md) | 前后端分层、登录/菜单契约、端到端时序与协作优化清单 |
| **2** | [02-可插拔鉴权与SpringSecurity接入方案.md](./02-可插拔鉴权与SpringSecurity接入方案.md) | **保留 Shiro+JWT，同时可切换 Spring Security** 的端口与适配器方案 |
| **3** | [03-Caffeine与Redis双重缓存与会话瘦身.md](./03-Caffeine与Redis双重缓存与会话瘦身.md) | 会话瘦身 + Caffeine(L1)/Redis(L2) 角色级 RBAC 缓存 |
| **4** | [04-SSE推送与在线沟通MVP.md](./04-SSE推送与在线沟通MVP.md) | SSE 预警推送 + 在线聊天/WebRTC 信令 MVP（与 WS 并存） |
| **5** | [05-SSL-PEM证书热更新.md](./05-SSL-PEM证书热更新.md) | CER+KEY 直导、路径只配一处、热更新与「为何曾双路径」说明 |
| **6** | [06-实时推送方案选型与WebSocket改造.md](./06-实时推送方案选型与WebSocket改造.md) | 中后台推送选型、复用旧 WS 做 IM、握手失败根因与改造 |
| **7** | [07-数据权限使用说明.md](./07-数据权限使用说明.md) | 角色 data_scope 配置、用户/部门接入、新业务怎么挂 `@DataScope` |
| **8** | [08-SpringSecurity下的数据权限与Jeecg对照.md](./08-SpringSecurity下的数据权限与Jeecg对照.md) | 切 Security 后数据权限怎么做；对照 / 迁移阶段 |
| **9** | [09-多租户SaaS改造技术方案.md](./09-多租户SaaS改造技术方案.md) | 后期共享库+tenant_id 多租户；与 data_scope/缓存/鉴权正交的分阶段规划 |
| **10** | [10-SpringAOP切面为何生效与starter-aop.md](./10-SpringAOP切面为何生效与starter-aop.md) | 为何日志/限流/防重能织入；aspectjweaver 与 starter-aop；DataScope 问题根因对照 |
| **11** | [11-简历工具AI扩展-解析分析岗位搜索PPT.md](./11-简历工具AI扩展-解析分析岗位搜索PPT.md) | 简历附件解析修复；岗位分析/搜索/PPT API 契约 |
| **13** | [13-文件管理大文本读取优化.md](./13-文件管理大文本读取优化.md) | read-text 限长/限体积；前端原生 textarea 避免大文本卡顿 |
| **14** | [14-骨架屏识别API.md](./14-骨架屏识别API.md) | P2 URL 抓取 / P3 图片 AI → 骨架 Schema 契约 |
| **15** | [15-代码生成发布菜单.md](./15-代码生成发布菜单.md) | ZIP 与发布解耦；幂等菜单、事务、审计 |
| **16** | [16-代码生成双前端模板-uiType.md](./16-代码生成双前端模板-uiType.md) | Element / Ant Design Vue 双模板；`uiType`；antd 动态条件；Bootstrap HTML 静态页 |
| **17** | [17-动态条件查询.md](./17-动态条件查询.md) | BaseEntity.conditionsJson + DynamicQueryHelper + MyBatis 公共片段；用户列表已接入 |

---

## 可插拔鉴权（结论摘要）

**可以**在保留现有 Shiro + JWT + Redis 会话的同时，接入可切换的 Spring Security 实现：

1. 业务只依赖 `TokenSessionPort` / `SecurityFacade`（及自研权限注解）  
2. `SysUserTokenService`、`RbacCacheService` 作为共用层，两套适配器都调用它们  
3. 配置 `geekplus.security.provider=shiro|spring-security`，**同一时刻只启用一套 Filter**  
4. 前端默认继续 `Plus-Token`；若改 Bearer，只改前端 `request.js` 两处  

细节与阶段划分见 [第 2 篇](./02-可插拔鉴权与SpringSecurity接入方案.md)。

---

## 与前端文档的分工

| 内容 | 仓库 |
|------|------|
| BlogShell、双重动态路由、request 调度、导航/轮播/分享卡 | 前端 `docs/v2/00–07` |
| 全栈契约、鉴权可插拔、会话/RBAC 缓存 | **本目录** |

前端索引：[geekplus-blog-frontend/docs/v2/README.md](../../geekplus-blog-frontend/docs/v2/README.md)（若相对路径因目录布局不同，请按本机兄弟仓库打开）。
