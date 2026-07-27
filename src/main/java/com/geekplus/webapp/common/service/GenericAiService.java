package com.geekplus.webapp.common.service;

import com.geekplus.common.ai.AiRuntimeConfig;
import com.geekplus.common.dto.GenericAiRequest;
import com.geekplus.common.util.google.GeminiUtils;
import com.geekplus.common.util.string.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 AI 请求服务：按 AI 源组装 URL+Key，支持 GET/POST 实测连通性。
 * 供 ChatAIController、后台 AI 源「测试」共用。
 */
@Slf4j
@Service
public class GenericAiService {

    private final AiSourceService aiSourceService;

    public GenericAiService(AiSourceService aiSourceService) {
        this.aiSourceService = aiSourceService;
    }

    /** 仅预览：返回将要请求的 method / url / keyMasked / body */
    public Map<String, Object> preview(GenericAiRequest request) {
        return buildResult(request, null, null, null, 0L, true);
    }

    /** 执行一次通用请求并返回响应 */
    public Map<String, Object> execute(GenericAiRequest request) throws Exception {
        Prepared prepared = prepare(request);
        long start = System.currentTimeMillis();
        HttpResult http = send(prepared.method, prepared.url, prepared.body, prepared.headers);
        long cost = System.currentTimeMillis() - start;
        return buildResult(request, prepared, http, null, cost, false);
    }

    private Map<String, Object> buildResult(GenericAiRequest request, Prepared prepared,
                                            HttpResult http, String error, long costMs, boolean previewOnly) {
        if (prepared == null) {
            prepared = prepare(request);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", prepared.provider);
        data.put("model", prepared.model);
        data.put("method", prepared.method);
        data.put("url", prepared.displayUrl);
        data.put("urlWithKey", prepared.displayUrlWithKey);
        data.put("apiKeyMasked", prepared.keyMasked);
        data.put("requestBody", prepared.body);
        data.put("previewOnly", previewOnly);
        if (!previewOnly) {
            data.put("costMs", costMs);
            if (http != null) {
                data.put("httpStatus", http.status);
                data.put("response", http.body);
            }
            if (error != null) {
                data.put("error", error);
            }
        }
        return data;
    }

    private Prepared prepare(GenericAiRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String method = StringUtils.isEmpty(request.getMethod()) ? "POST" : request.getMethod().trim().toUpperCase(Locale.ROOT);
        if (!"GET".equals(method) && !"POST".equals(method)) {
            throw new IllegalArgumentException("method 仅支持 GET 或 POST");
        }

        AiRuntimeConfig cfg = aiSourceService.resolve(request.getSourceId(), request.getProvider(), request.getModel());
        String provider = cfg != null && StringUtils.isNotEmpty(cfg.getProvider())
                ? cfg.getProvider().toLowerCase(Locale.ROOT) : "gemini";
        if ("openai".equals(provider)) {
            provider = "chatgpt";
        }

        String apiKey = firstNonEmpty(request.getApiKey(), cfg != null ? cfg.getApiKey() : null);
        String model = firstNonEmpty(request.getModel(), cfg != null ? cfg.getModel() : null);
        boolean gemini = "gemini".equals(provider);
        String apiUrl = firstNonEmpty(request.getApiUrl(),
                cfg != null ? (gemini ? cfg.getBaseUrl() : cfg.getApiUrl()) : null);
        String prompt = request.getPrompt() == null ? "" : request.getPrompt();

        Prepared p = new Prepared();
        p.provider = provider;
        p.model = model;
        p.method = method;
        p.apiKey = apiKey == null ? "" : apiKey.trim();
        p.keyMasked = maskKey(p.apiKey);

        if (gemini) {
            prepareGemini(p, apiUrl, model, prompt, method);
        } else {
            // chatgpt + 任意自定义提供方：OpenAI 兼容协议（通用 AI 服务）
            if (StringUtils.isEmpty(apiUrl) && !"chatgpt".equals(provider)) {
                throw new IllegalArgumentException("自定义提供方必须配置 API URL（OpenAI 兼容 chat/completions 地址）");
            }
            prepareChatgpt(p, apiUrl, model, prompt, method);
        }
        return p;
    }

    private void prepareGemini(Prepared p, String apiUrl, String model, String prompt, String method) {
        String base = GeminiUtils.normalizeBaseUrl(apiUrl);
        String m = StringUtils.isEmpty(model) ? GeminiUtils.DEFAULT_MODEL : model.trim();
        if (m.startsWith("models/")) {
            m = m.substring("models/".length());
        }
        p.model = m;

        if ("GET".equals(method)) {
            // GET：默认 models.list；若 apiUrl 已是完整路径则直接用
            String url;
            if (StringUtils.isNotEmpty(apiUrl) && apiUrl.contains("/models/") && !apiUrl.contains(":generateContent")) {
                url = apiUrl.contains("?") ? apiUrl : (apiUrl + (apiUrl.endsWith("/") ? "" : ""));
                if (!url.contains("key=")) {
                    url = appendQuery(url, "key", p.apiKey);
                }
            } else {
                url = base + "/models?key=" + urlEncode(p.apiKey);
                if (StringUtils.isNotEmpty(prompt)) {
                    // 可选：把 prompt 当作 pageSize / 过滤提示，不拼进 Google 官方参数；仅作自定义 query note
                    url = appendQuery(url, "pageSize", "5");
                }
            }
            p.url = url;
            p.body = null;
            p.headers = new LinkedHashMap<>();
            p.headers.put("Accept", "application/json");
        } else {
            String generateUrl = GeminiUtils.buildGenerateUrl(base, m);
            // POST：key 放 query，与官方 curl 示例一致，便于中间区域展示 url+key
            p.url = generateUrl + "?key=" + urlEncode(p.apiKey);
            p.body = buildGeminiBody(prompt);
            p.headers = new LinkedHashMap<>();
            p.headers.put("Content-Type", "application/json; charset=UTF-8");
            p.headers.put("Accept", "application/json");
        }
        fillDisplayUrls(p);
    }

    private void prepareChatgpt(Prepared p, String apiUrl, String model, String prompt, String method) {
        String url = StringUtils.isNotEmpty(apiUrl)
                ? apiUrl.trim()
                : "https://api.openai.com/v1/chat/completions";
        p.url = url;
        p.headers = new LinkedHashMap<>();
        p.headers.put("Content-Type", "application/json; charset=UTF-8");
        p.headers.put("Accept", "application/json");
        if (StringUtils.isNotEmpty(p.apiKey)) {
            p.headers.put("Authorization", "Bearer " + p.apiKey);
        }
        if ("GET".equals(method)) {
            // OpenAI chat 一般不用 GET；若用户选 GET，仍打同一 URL（便于测网关/代理）
            p.body = null;
            if (StringUtils.isNotEmpty(prompt)) {
                p.url = appendQuery(p.url, "q", prompt);
            }
        } else {
            String m = StringUtils.isEmpty(model) ? "gpt-4o-mini" : model;
            p.model = m;
            p.body = buildChatgptBody(m, prompt);
        }
        fillDisplayUrls(p);
    }

    private void fillDisplayUrls(Prepared p) {
        p.displayUrl = maskKeyInUrl(p.url);
        p.displayUrlWithKey = p.url;
        // 中间区域展示：url + key 组合说明（非 Gemini 均 Bearer）
        if (!"gemini".equals(p.provider)) {
            p.displayUrlWithKey = p.displayUrl + "\nAuthorization: Bearer " + p.keyMasked;
        }
    }

    private String buildGeminiBody(String prompt) {
        String text = prompt == null ? "" : prompt;
        // 若已是 JSON 对象，原样作为 body
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        String escaped = escapeJson(text);
        return "{\"contents\":[{\"parts\":[{\"text\":\"" + escaped + "\"}]}]}";
    }

    private String buildChatgptBody(String model, String prompt) {
        String text = prompt == null ? "" : prompt;
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        return "{\"model\":\"" + escapeJson(model) + "\",\"messages\":[{\"role\":\"user\",\"content\":\""
                + escapeJson(text) + "\"}]}";
    }

    private HttpResult send(String method, String url, String body, Map<String, String> headers) throws Exception {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(90000);
            conn.setDoInput(true);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            if ("POST".equals(method)) {
                conn.setDoOutput(true);
                byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                    os.flush();
                }
            } else {
                conn.setDoOutput(false);
            }
            conn.connect();
            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String resp = "";
            if (stream != null) {
                reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                resp = sb.toString().trim();
            }
            return new HttpResult(status, resp);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignore) {
                    // ignore
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String firstNonEmpty(String a, String b) {
        if (StringUtils.isNotEmpty(a)) {
            return a.trim();
        }
        if (StringUtils.isNotEmpty(b)) {
            return b.trim();
        }
        return null;
    }

    private static String maskKey(String key) {
        if (StringUtils.isEmpty(key)) {
            return "(empty)";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private static String maskKeyInUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("([?&]key=)[^&]*", "$1***");
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return s == null ? "" : s;
        }
    }

    private static String appendQuery(String url, String name, String value) {
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + name + "=" + urlEncode(value);
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static class Prepared {
        String provider;
        String model;
        String method;
        String apiKey;
        String keyMasked;
        String url;
        String displayUrl;
        String displayUrlWithKey;
        String body;
        Map<String, String> headers;
    }

    private static class HttpResult {
        final int status;
        final String body;

        HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
