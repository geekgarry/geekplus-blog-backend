# Spring Boot 2.7 SSL：直接导入 CER + KEY（替代 PFX）与热更新

## 结论

可以。嵌入式 Tomcat 支持 PEM：

- `server.ssl.certificate` ← `fullchain.cer`（推荐）或域名 `.cer`
- `server.ssl.certificate-private-key` ← `.key`
- 可选 `server.ssl.certificate-chain` ← `ca.cer`（fullchain 已含链则不必）

不必再 `openssl pkcs12` 转成 pfx。

热更新组件 `SSLCertificateReload` **直接复用上述路径**，`server.ssl.reload` 只保留行为配置（开关、模式、检查间隔）。

---

## 面板文件怎么用

| 文件 | 用途 |
|------|------|
| `fullchain.cer` | **首选证书文件**（域名证+中间链） |
| `www.xxx.cer` | 仅域名证；若不用 fullchain，需再配 ca 链 |
| `www.xxx.key` | 私钥（权限建议 600） |
| `ca.cer` | 中间/根；fullchain 已含则可不用 |
| `.csr` / `.conf` | 申请用，运行时不需要 |

---

## 配置（路径只配一处）

```yaml
server:
  ssl:
    certificate: file:/path/fullchain.cer
    certificate-private-key: file:/path/xxx.key
    key-password: "可选"
    reload:
      enabled: true
      mode: auto          # auto | pem | pkcs12；有 PEM 则走 PEM
      check-interval-ms: 60000
```

回退 PKCS12：配 `key-store` / `key-store-password`，并设 `reload.mode: pkcs12`（或 `auto` 且不配 PEM）。

---

## 为什么曾经在 reload 里再写一遍路径？

早期热更新实现里，出现过类似：

```yaml
server:
  ssl:
    certificate: file:/path/A/fullchain.cer
    certificate-private-key: file:/path/A/xxx.key
    reload:
      certificate-file: /path/B/fullchain.cer   # 又写一遍
      certificate-key-file: /path/B/xxx.key
```

### 当时常见动机（不代表业务上应该这么用）

| 动机 | 实际含义 |
|------|----------|
| 启动走 Spring Boot 标准项，热更新自己拼 Tomcat Connector | 热更新代码要读磁盘路径，作者另开一组 `reload.*-file` |
| 担心 `file:` 前缀与裸路径解析不一致 | 启动用 `file:/...`，watcher 用裸路径 |
| 预留「启动用旧证、运行时热更到另一目录」 | 两套路径可以指向不同文件 |

这些都能做，但**单机博客 / acme 覆盖同目录续期**并不需要，重复配置只会增加漏改风险。

### 三种策略对比

#### 1. 两套路径可以不一样（旧写法允许的）

- 启动：`server.ssl.certificate = A`
- 热更新监听/替换：`reload.certificate-file = B`
- **含义**：进程起来用 A；以后只热更 B。
- **适合**：蓝绿/灰度、临时试新证、启动证与续期目录刻意分离。
- **代价**：续期改错一边、两边不一致、排障难。

#### 2. 两套路径故意配成一样

- 功能上等于「一个路径」，但 yml 仍写两次。
- **问题**：改路径要改两处，漏改一次就会「启动正常、热更新看错文件」。

#### 3. 只用一套（当前推荐默认）

- 只配 `server.ssl.certificate` + `certificate-private-key`。
- 启动与热更新都读这组；`reload` 只管 `enabled` / `mode` / `check-interval-ms`。
- **适合**：acme / 面板续期覆盖同名文件的场景（本站默认）。

### 本节结论

- **可以不一样**：技术上成立，属于「双源证书」高级用法，不是默认需求。
- **配成一样**：没必要，纯重复。
- **直接用一个**：对本站最合适——续期覆盖原文件，热更新监听同一路径即可。

因此：reload 里再写路径，**不代表业务上应该和上面不同**；只是实现时多留了一层配置。现已收成一套路径。

---

## 热更新行为

`SSLCertificateReload`：

1. 监听 `certificate` / `certificate-private-key`（及可选 chain）的 mtime  
2. 变更后替换 HTTPS Connector  
3. `POST /system/ssl/deploy`：上传到上述配置路径并立刻 reload  
4. `POST /system/ssl/reload`：仅触发加载（文件已手动覆盖时）  

权限码：`system:ssl:view` / `system:ssl:deploy`（按菜单授权）。

---

## 部署步骤

1. 把面板的 `fullchain.cer`、`xxx.key` 放到固定目录  
2. `chmod 600 xxx.key`（不要 `0777`）  
3. yml 只改 `server.ssl.certificate` / `certificate-private-key`，**重启一次**（首次从 pfx 切 PEM）  
4. 以后续期：覆盖同名文件，或调 deploy → 约 1 分钟内自动热更（或立即 reload）

---

## 注意

- 私钥权限务必收紧  
- 证书类型用 Tomcat `UNDEFINED`，兼容 RSA/EC  
- 热更新会短暂停 HTTPS Connector，极端情况下有瞬时断连  
- Spring Boot 的 `trust-certificate` 语义偏信任库，与「服务端证书链」不同；独立链请用 `server.ssl.certificate-chain`（本组件支持），或直接用 fullchain  
