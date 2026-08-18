# GeekPlus-Blog-API · 极客普拉斯博客后端

个人 / 团队博客一体站 API：**访客可读的内容接口** + **运营可配的系统管理**。

技术栈以 **Spring Boot + MyBatis + Redis** 为主，权限框架采用 **Apache Shiro + JWT**（无状态 Token），支持 Caffeine + Redis 双重缓存与会话瘦身。

---

## 目录

- [项目简介](#项目简介)
- [功能一览](#功能一览)
- [技术架构](#技术架构)
- [目录结构](#目录结构)
- [快速开始](#快速开始)
- [项目配置](#项目配置)

---

## 项目简介

| 维度 | 说明 |
|------|------|
| 产品名 | 极客普拉斯 / GeekPlus Blog |
| 仓库角色 | 博客前台 + 管理后台共用的 REST API（本仓库） |
| 前端 | 独立 SPA（`geekplus-blog-frontend`），开发代理至本服务 |
| 典型用户 | 访客逛站、作者投稿、管理员配置栏目/文章/站点与 RBAC |

业务上可粗分成三条线：

1. **内容服务**：文章 / 栏目 / 标签 / 评论 / 轮播 / 站点统计 / 文件中转等  
2. **系统管理**：用户、角色、菜单权限、部门、字典、通知、日志、在线用户等  
3. **扩展工具**：代码生成（Freemarker）、Quartz 定时任务、简历工具、AI 助手、WebSocket 推送等  

前后端分离；默认登录密码为 `123456`（以库内数据为准）。

前端仓库：[geekplus-blog-frontend](https://github.com/geekgarry/geekplus-blog-frontend)（当前主开发多为 `geekplus-blog-frontend-3.1.0`）。

---

## 功能一览

### 博客业务

- 文章 CRUD、分类、标签映射、评论与留言  
- 轮播、友链、站点公告与访问统计  
- 文件上传 / 下载、文件中转（访客端 + 管理端）  
- 前台匿名接口（如 `/geekplusapp/**`）与签名资源访问  

### 系统管理

- 用户 / 角色 / 菜单权限（RBAC）、部门与数据权限  
- 登录鉴权：`Plus-Token` 头 / Cookie，JWT + Redis 会话  
- 系统通知、操作日志、登录日志、在线用户  
- 字典、参数配置、服务监控  

### 工具与基础设施

- Freemarker 代码生成（逆向 / 模版扩展业务）  
- Quartz 定时任务管理  
- WebSocket / Socket.IO 消息推送（生产注意 SSL 配置）  
- AI 对话（可配 Gemini / ChatGPT 等）  
- 简历模板与相关工具接口  

---

## 技术架构

```text
HTTP
  → IpRateLimitFilter（可选）
  → Shiro Filter 链
       ├─ anon：登录、验证码、/geekplusapp/**、部分下载与 WS …
       ├─ signedAnon：/profile/** 等签名资源
       └─ jwt：其余路径 → JwtFilter → JwtRealm
            → SysUserTokenService（验 JWT + Redis LoginUser + 在线 JTI）
            → 授权时 RbacCacheService.resolvePerms
  → Controller（@RequiresPermissions / 业务）
       ├─ MySQL（MyBatis + Druid）
       ├─ Redis（会话 / 在线 JTI / L2 缓存）
       └─ Caffeine（L1 热点 RBAC / 配置）
```

| 层 | 选型 |
|----|------|
| 运行时 | Spring Boot 2.7.x |
| 安全 | Apache Shiro + 自研 JWT（`jwt-plus`）；可插拔 Spring Security 方案见 `docs/02` |
| 持久化 | MyBatis + MySQL + Druid |
| 缓存 | Redis；热点 RBAC/配置走 `TwoLevelCache`（Caffeine + Redis） |
| 其它 | WebSocket、Quartz、Freemarker 代码生成、文件与签名资源等 |

技术文档索引：[`docs/README.md`](./docs/README.md)（全栈契约、可插拔鉴权、会话瘦身、[数据权限用法](./docs/07-数据权限使用说明.md)）。

---

## 目录结构

```text
├── docs/                         # 技术文档（架构 / 鉴权 / 缓存）
├── sql/                          # 库表与迁移脚本
├── src/main/java/com/geekplus/
│   ├── GeekPlusBlogApplication.java
│   ├── common/                   # 通用工具、注解、缓存、Redis、领域模型
│   ├── framework/                # Shiro/JWT、Filter、Aspect、配置、Socket
│   └── webapp/
│       ├── common/               # 登录、验证码、上传下载、监控、AI 等
│       ├── system/               # 系统管理（用户/角色/菜单/部门/日志…）
│       ├── function/             # 博客业务（文章/评论/轮播/文件中转…）
│       ├── file/                 # 文件管理
│       └── tool/                 # 代码生成、Quartz、简历等
├── src/main/resources/
│   ├── application.yml           # 激活 profile
│   ├── application-dev.yml       # 开发配置
│   ├── application-prod.yml      # 生产配置
│   ├── mybatis/                   # Mapper XML
│   └── db/ / generator/          # 其它资源
├── pom.xml
└── README.md
```

---

## 快速开始

**前置：** JDK 8+、Maven 3.x、MySQL、Redis。

```bash
# 1. 导入 sql/ 下脚本，创建库（如 geekplusblog）并初始化表数据

# 2. 修改开发配置中的数据源与 Redis
#    src/main/resources/application-dev.yml

# 3. 本地启动（建议先切到 dev profile）
#    application.yml 中 spring.profiles.active=dev
mvn spring-boot:run

# 或打包后运行
mvn clean package -DskipTests
java -jar target/*.jar
```

开发默认端口见 `application-dev.yml`（如 `9002`）。前端通过 `/dev-api` 或 `/pro-api` 反代到本服务。

WebSocket 主动推送相关代码在 `common` / `framework` 下（如 Socket 服务与配置）；生产环境若走 HTTPS，需同步配置 SSL。

---

## 项目配置

常用配置集中在 `application-dev.yml` / `application-prod.yml`（以文件内实际值为准，勿把密钥提交到公开仓库）。

| 配置项 | 含义 |
|--------|------|
| `spring.profiles.active` | 激活环境：`dev` / `prod`（见 `application.yml`） |
| `server.port` | HTTP 端口 |
| `spring.datasource.druid.*` | MySQL 连接与连接池 |
| `spring.redis.*` | Redis 地址、密码、连接池 |
| `token.header` | 鉴权头名，默认 `Plus-Token` |
| `token.expireTime` | 会话 TTL（秒），如 30 天滑动续期 |
| `token.residueRefreshTime` | 剩余寿命低于该值时重签 JWT |
| `token.ssoEnabled` | 是否开启 SSO（可被库表配置覆盖） |
| `token.secret` | JWT 签名密钥 |
| `geekplus.profile` | 服务器文件存储根路径 |
| `geekplus.captchaType` | 验证码类型：`math` / `char` |
| `geekplus.addressEnabled` | 是否解析登录 IP 归属 |
| `ai.*` | 默认 AI 提供商与模型（可被后台数据源覆盖） |
| `swagger.enable` | 是否开启 Swagger |

更细的登录 / 菜单契约、Filter 链与缓存键设计见 [`docs/`](./docs/README.md)。
