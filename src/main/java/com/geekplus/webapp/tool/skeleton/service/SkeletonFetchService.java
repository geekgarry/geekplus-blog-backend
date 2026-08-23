package com.geekplus.webapp.tool.skeleton.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 骨架识别 P2：服务端拉取公开页 HTML，去掉脚本样式后交给前端 DOM 启发式。
 * 不执行 JS（SPA 动态结构可能不全，属预期限制）。
 */
@Service
public class SkeletonFetchService {

    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_BODY_CHARS = 800_000;

    public Map<String, Object> fetchHtml(String url) throws Exception {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("url 不能为空");
        }
        String u = url.trim();
        URI uri = URI.create(u);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("仅支持 http/https");
        }
        // 简单拦内网常见主机，降低 SSRF 误用风险（非完备防火墙）
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty()
                || "localhost".equals(host)
                || host.endsWith(".local")
                || host.startsWith("127.")
                || host.startsWith("10.")
                || host.startsWith("192.168.")
                || host.startsWith("169.254.")) {
            throw new IllegalArgumentException("不允许抓取本机或内网地址");
        }

        Connection.Response resp = Jsoup.connect(u)
                .userAgent("GeekPlus-SkeletonBot/1.0 (+https://geekplus)")
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .maxBodySize(2_000_000)
                .execute();
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("抓取失败 HTTP " + resp.statusCode());
        }
        Document doc = resp.parse();
        doc.select("script, style, noscript, iframe, svg title").remove();
        String title = doc.title();
        String bodyHtml = doc.body() != null ? doc.body().html() : doc.html();
        if (bodyHtml.length() > MAX_BODY_CHARS) {
            bodyHtml = bodyHtml.substring(0, MAX_BODY_CHARS);
        }

        Map<String, Object> out = new HashMap<>(4);
        out.put("url", u);
        out.put("title", title);
        out.put("html", bodyHtml);
        out.put("status", resp.statusCode());
        return out;
    }
}
