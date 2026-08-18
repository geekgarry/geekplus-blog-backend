package com.geekplus.webapp.common.controller;

import com.geekplus.common.domain.Result;
import com.geekplus.framework.config.SSLCertificateReload;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * 在线部署 / 热加载 SSL：路径取自 server.ssl.certificate / certificate-private-key（与启动配置同一套）。
 */
@Slf4j
@RestController
@RequestMapping("/system/ssl")
public class SslCertificateController {

    private final ObjectProvider<SSLCertificateReload> reloadProvider;

    public SslCertificateController(ObjectProvider<SSLCertificateReload> reloadProvider) {
        this.reloadProvider = reloadProvider;
    }

    @GetMapping("/status")
    @RequiresPermissions("system:ssl:view")
    public Result status() {
        SSLCertificateReload reload = reloadProvider.getIfAvailable();
        Map<String, Object> data = new HashMap<>();
        if (reload == null) {
            data.put("reloadBean", false);
            return Result.success(data);
        }
        String cert = SSLCertificateReload.normalizePath(reload.getCertificate());
        String key = SSLCertificateReload.normalizePath(reload.getCertificatePrivateKey());
        String chain = SSLCertificateReload.normalizePath(reload.getCertificateChain());
        data.put("reloadBean", true);
        data.put("certificate", reload.getCertificate());
        data.put("certificatePrivateKey", reload.getCertificatePrivateKey());
        data.put("certificateChain", reload.getCertificateChain());
        data.put("certExists", exists(cert));
        data.put("keyExists", exists(key));
        data.put("chainExists", exists(chain));
        return Result.success(data);
    }

    /**
     * 上传 PEM 到 server.ssl.certificate / certificate-private-key 指向的路径并热加载。
     */
    @PostMapping("/deploy")
    @RequiresPermissions("system:ssl:deploy")
    public Result deploy(@RequestParam("certFile") MultipartFile certFile,
                         @RequestParam("keyFile") MultipartFile keyFile,
                         @RequestParam(value = "chainFile", required = false) MultipartFile chainFile) throws IOException {
        SSLCertificateReload reload = reloadProvider.getIfAvailable();
        if (reload == null) {
            return Result.error("热更新组件未启用（server.ssl.reload.enabled=false）");
        }
        String certPath = SSLCertificateReload.normalizePath(reload.getCertificate());
        String keyPath = SSLCertificateReload.normalizePath(reload.getCertificatePrivateKey());
        if (!StringUtils.hasText(certPath) || !StringUtils.hasText(keyPath)) {
            return Result.error("未配置 server.ssl.certificate / certificate-private-key");
        }
        if (certFile == null || certFile.isEmpty() || keyFile == null || keyFile.isEmpty()) {
            return Result.error("请同时上传证书(.cer/.pem)与私钥(.key)");
        }
        writeSafely(certFile, certPath);
        writeSafely(keyFile, keyPath);
        String chainPath = SSLCertificateReload.normalizePath(reload.getCertificateChain());
        if (chainFile != null && !chainFile.isEmpty() && StringUtils.hasText(chainPath)) {
            writeSafely(chainFile, chainPath);
        }

        boolean ok = reload.reloadSslContext();
        return ok ? Result.success("证书已部署并热加载") : Result.error("文件已写入，但热加载失败，请查看日志");
    }

    @PostMapping("/reload")
    @RequiresPermissions("system:ssl:deploy")
    public Result reload() {
        SSLCertificateReload reload = reloadProvider.getIfAvailable();
        if (reload == null) {
            return Result.error("热更新组件未启用");
        }
        return reload.reloadSslContext() ? Result.success("已热加载") : Result.error("热加载失败");
    }

    private static void writeSafely(MultipartFile src, String destPath) throws IOException {
        Path dest = Paths.get(destPath);
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = dest.resolveSibling(dest.getFileName() + ".uploading");
        Files.copy(src.getInputStream(), tmp, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        File f = dest.toFile();
        //noinspection ResultOfMethodCallIgnored
        f.setReadable(false, false);
        //noinspection ResultOfMethodCallIgnored
        f.setReadable(true, true);
        //noinspection ResultOfMethodCallIgnored
        f.setWritable(true, true);
        log.info("已写入证书文件: {}", dest);
    }

    private static boolean exists(String path) {
        return StringUtils.hasText(path) && new File(path).isFile();
    }
}
