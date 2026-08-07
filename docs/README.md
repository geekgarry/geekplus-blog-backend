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
