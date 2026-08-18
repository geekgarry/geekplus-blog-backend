package com.geekplus.framework.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSL 证书在线热加载。
 * <p>
 * 证书路径直接复用 Spring Boot 标准配置，不再在 reload 下重复配置：
 * <ul>
 *   <li>PEM：{@code server.ssl.certificate} + {@code server.ssl.certificate-private-key}</li>
 *   <li>PKCS12：{@code server.ssl.key-store} + {@code server.ssl.key-store-password}</li>
 * </ul>
 * {@code server.ssl.reload} 仅保留开关、模式、检查间隔等热更新行为参数。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "server.ssl.reload", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SSLCertificateReload implements ApplicationListener<WebServerInitializedEvent> {

    /**
     * pem | pkcs12 | auto（有 certificate+private-key 则 pem，否则走 key-store）
     */
    @Value("${server.ssl.reload.mode:auto}")
    private String mode;

    @Value("${server.port:8443}")
    private int httpsPort;

    @Value("${server.ssl.host-name:}")
    private String sslHostName;

    /** Spring Boot 标准 PEM 证书（可为 fullchain） */
    @Getter
    @Value("${server.ssl.certificate:}")
    private String certificate;

    /** Spring Boot 标准私钥 */
    @Getter
    @Value("${server.ssl.certificate-private-key:}")
    private String certificatePrivateKey;

    /**
     * 可选：独立证书链。Spring Boot 的 trust-certificate 语义不同，
     * 若 fullchain 已含链可留空；需要单独链时用本项。
     */
    @Getter
    @Value("${server.ssl.certificate-chain:}")
    private String certificateChain;

    /** 私钥口令（加密 key 时） */
    @Value("${server.ssl.key-password:}")
    private String keyPassword;

    /** 兼容旧 PKCS12 */
    @Getter
    @Value("${server.ssl.key-store:}")
    private String keystorePath;

    @Value("${server.ssl.key-store-password:}")
    private String keystorePassword;

    @Value("${server.ssl.key-store-type:PKCS12}")
    private String keystoreType;

    @Value("${server.ssl.reload.check-interval-ms:60000}")
    private long checkIntervalMs;

    private TomcatWebServer tomcatWebServer;
    private Connector httpsConnector;
    private volatile long lastFingerprint = -1L;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile boolean running = true;
    private Thread updaterThread;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (!(event.getWebServer() instanceof TomcatWebServer) || !initialized.compareAndSet(false, true)) {
            return;
        }
        this.tomcatWebServer = (TomcatWebServer) event.getWebServer();
        for (Connector connector : tomcatWebServer.getTomcat().getService().findConnectors()) {
            if ("https".equalsIgnoreCase(connector.getScheme())) {
                this.httpsConnector = connector;
                log.info("SSL 热更新：已绑定 HTTPS Connector, port={}, resolvedMode={}", httpsPort, resolveMode());
                break;
            }
        }
        if (httpsConnector == null) {
            log.warn("SSL 热更新：未找到 HTTPS Connector，请确认 server.ssl 已启用");
            return;
        }
        lastFingerprint = currentFingerprint();
        startWatcher();
    }

    private void startWatcher() {
        updaterThread = new Thread(this::watchLoop, "SSL-Cert-Updater");
        updaterThread.setDaemon(true);
        updaterThread.start();
        log.info("SSL 证书热更新线程已启动，检查间隔 {} ms", checkIntervalMs);
    }

    private void watchLoop() {
        while (running) {
            try {
                Thread.sleep(Math.max(5000L, checkIntervalMs));
                long fp = currentFingerprint();
                if (fp > 0 && fp != lastFingerprint) {
                    log.info("检测到证书文件变更，开始热更新… fingerprint {} -> {}", lastFingerprint, fp);
                    if (reloadSslContext()) {
                        lastFingerprint = fp;
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("SSL 证书检查/热更新异常", e);
            }
        }
    }

    /** 对外：上传/覆盖证书后可主动触发 */
    public synchronized boolean reloadSslContext() {
        if (tomcatWebServer == null || httpsConnector == null) {
            log.warn("Tomcat 未就绪，无法热更新证书");
            return false;
        }
        if (!validateFiles()) {
            return false;
        }
        try {
            httpsConnector.pause();
            httpsConnector.stop();
            tomcatWebServer.getTomcat().getService().removeConnector(httpsConnector);

            Connector newConnector = createHttpsConnector();
            tomcatWebServer.getTomcat().getService().addConnector(newConnector);
            newConnector.start();
            this.httpsConnector = newConnector;
            log.info("SSL 证书热更新成功（mode={}）", resolveMode());
            return true;
        } catch (Exception e) {
            log.error("SSL 证书热更新失败", e);
            return false;
        }
    }

    private Connector createHttpsConnector() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setScheme("https");
        connector.setPort(httpsPort);
        connector.setSecure(true);

        Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
        protocol.setSSLEnabled(true);

        String host = StringUtils.hasText(sslHostName) ? sslHostName : "_default_";
        protocol.setDefaultSSLHostConfigName(host);

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setHostName(host);

        SSLHostConfigCertificate cert = new SSLHostConfigCertificate(
                sslHostConfig, SSLHostConfigCertificate.Type.UNDEFINED);

        if (isPemMode()) {
            cert.setCertificateFile(normalizePath(certificate));
            cert.setCertificateKeyFile(normalizePath(certificatePrivateKey));
            if (StringUtils.hasText(keyPassword)) {
                cert.setCertificateKeyPassword(keyPassword);
            }
            if (StringUtils.hasText(certificateChain)) {
                cert.setCertificateChainFile(normalizePath(certificateChain));
            }
            log.info("使用 PEM 证书: cert={}, key={}", certificate, certificatePrivateKey);
        } else {
            cert.setCertificateKeystoreFile(normalizePath(keystorePath));
            cert.setCertificateKeystorePassword(keystorePassword);
            cert.setCertificateKeystoreType(
                    StringUtils.hasText(keystoreType) ? keystoreType : "PKCS12");
            log.info("使用 KeyStore 证书: path={}, type={}", keystorePath, keystoreType);
        }

        sslHostConfig.addCertificate(cert);
        connector.addSslHostConfig(sslHostConfig);
        return connector;
    }

    private String resolveMode() {
        if ("pem".equalsIgnoreCase(mode) || "pkcs12".equalsIgnoreCase(mode)) {
            return mode.toLowerCase();
        }
        // auto
        if (StringUtils.hasText(certificate) && StringUtils.hasText(certificatePrivateKey)) {
            return "pem";
        }
        return "pkcs12";
    }

    private boolean isPemMode() {
        return "pem".equalsIgnoreCase(resolveMode());
    }

    private boolean validateFiles() {
        if (isPemMode()) {
            File cert = new File(normalizePath(certificate));
            File key = new File(normalizePath(certificatePrivateKey));
            if (!cert.isFile() || !key.isFile()) {
                log.error("PEM 证书文件缺失: cert exists={}, key exists={} (检查 server.ssl.certificate / certificate-private-key)",
                        cert.isFile(), key.isFile());
                return false;
            }
            if (StringUtils.hasText(certificateChain)) {
                File chain = new File(normalizePath(certificateChain));
                if (!chain.isFile()) {
                    log.error("证书链文件不存在: {}", certificateChain);
                    return false;
                }
            }
            return true;
        }
        if (!StringUtils.hasText(keystorePath)) {
            log.error("PKCS12 模式未配置 server.ssl.key-store");
            return false;
        }
        File ks = new File(normalizePath(keystorePath));
        if (!ks.isFile()) {
            log.error("KeyStore 不存在: {}", keystorePath);
            return false;
        }
        return true;
    }

    private long currentFingerprint() {
        try {
            if (isPemMode()) {
                long fp = mtime(certificate) + mtime(certificatePrivateKey);
                if (StringUtils.hasText(certificateChain)) {
                    fp += mtime(certificateChain);
                }
                return fp;
            }
            return mtime(keystorePath);
        } catch (Exception e) {
            return -1L;
        }
    }

    private static long mtime(String path) {
        if (!StringUtils.hasText(path)) {
            return 0L;
        }
        File f = new File(normalizePath(path));
        return f.isFile() ? f.lastModified() : 0L;
    }

    /** 去掉 file: 前缀，得到磁盘路径（供上传覆盖与 mtime 检查） */
    public static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String p = path.trim();
        if (p.startsWith("file:")) {
            p = p.substring("file:".length());
            // file:///abs 或 file:/abs
            while (p.startsWith("//")) {
                p = p.substring(1);
            }
        }
        return p;
    }

    @PreDestroy
    public void destroy() {
        running = false;
        if (updaterThread != null) {
            updaterThread.interrupt();
        }
    }
}
