package com.geekplus.webapp.common.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.geekplus.common.ai.AiRuntimeConfig;
import com.geekplus.common.util.google.GeminiUtils;
import com.geekplus.common.util.string.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gemini 官方模型元数据查询（models.list / models.get）
 * 文档：https://ai.google.dev/api/models
 */
@Slf4j
@Service
public class GeminiModelService {

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    private final AiSourceService aiSourceService;

    public GeminiModelService(AiSourceService aiSourceService) {
        this.aiSourceService = aiSourceService;
    }

    /**
     * 对齐官方 models.list：
     * GET https://generativelanguage.googleapis.com/v1beta/models?key=$GEMINI_API_KEY
     * 自动翻页直到没有 nextPageToken。
     */
    public Map<String, Object> listModels(Long sourceId, String methodFilter, String keyword, Integer pageSize) throws Exception {
        AiRuntimeConfig cfg = resolveGemini(sourceId);
        String key = resolveApiKey(cfg);
        String baseUrl = GeminiUtils.normalizeBaseUrl(cfg != null ? cfg.getBaseUrl() : null);

        List<Map<String, Object>> all = new ArrayList<>();
        String pageToken = null;
        int guard = 0;
        // 官方默认 50；这里默认 50 更稳，避免部分环境对大 pageSize 异常
        int size = pageSize == null || pageSize <= 0 ? 50 : Math.min(pageSize, 1000);

        do {
            String raw = GeminiUtils.listModels(key, baseUrl, size, pageToken);
            if (StringUtils.isEmpty(raw)) {
                throw new IllegalStateException("models.list 返回空响应，请检查 API Key 与网络");
            }
            log.debug("models.list raw length={}", raw.length());
            JSONObject json = JSONObject.parseObject(raw);
            if (json == null) {
                throw new IllegalStateException("models.list 响应无法解析为 JSON: " + raw.substring(0, Math.min(200, raw.length())));
            }
            // 注意：fastjson 2.x 兼容层对缺失字段调用 getJSONObject/getJSONArray 会抛 JSONException("TODO")
            // 成功响应没有 error，必须用 get + 判空，不能写 getJSONObject("error") != null
            JSONObject err = asJSONObject(json.get("error"));
            if (err != null) {
                String msg = asString(err.get("message"));
                throw new IllegalStateException(StringUtils.isNotEmpty(msg) ? msg : String.valueOf(err));
            }
            JSONArray models = asJSONArray(json.get("models"));
            if (models != null) {
                for (int i = 0; i < models.size(); i++) {
                    JSONObject item = asJSONObject(models.get(i));
                    if (item != null) {
                        all.add(normalizeModel(item));
                    }
                }
            }
            pageToken = asString(json.get("nextPageToken"));
            guard++;
        } while (StringUtils.isNotEmpty(pageToken) && guard < 50);

        List<Map<String, Object>> filtered = all.stream()
                .filter(m -> matchMethod(m, methodFilter))
                .filter(m -> matchKeyword(m, keyword))
                .sorted(Comparator.comparing(m -> String.valueOf(m.getOrDefault("displayName", m.get("modelId"))),
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        // 与官方响应结构对齐的核心字段
        result.put("models", filtered);
        result.put("total", filtered.size());
        result.put("fetched", all.size());
        result.put("baseUrl", baseUrl);
        result.put("source", briefSource(cfg, key));
        result.put("methodStats", buildMethodStats(all));
        return result;
    }

    public Map<String, Object> getModel(Long sourceId, String modelName) throws Exception {
        AiRuntimeConfig cfg = resolveGemini(sourceId);
        String key = resolveApiKey(cfg);
        String baseUrl = GeminiUtils.normalizeBaseUrl(cfg != null ? cfg.getBaseUrl() : null);
        String raw = GeminiUtils.getModel(key, baseUrl, modelName);
        JSONObject json = JSONObject.parseObject(raw);
        if (json == null) {
            throw new IllegalStateException("models.get 响应无法解析为 JSON");
        }
        // 同 listModels：禁止 getJSONObject("error")，缺失字段时 fastjson 会抛 TODO
        JSONObject err = asJSONObject(json.get("error"));
        if (err != null) {
            String msg = asString(err.get("message"));
            throw new IllegalStateException(StringUtils.isNotEmpty(msg) ? msg : String.valueOf(err));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", briefSource(cfg, key));
        result.put("baseUrl", baseUrl);
        result.put("model", normalizeModel(json));
        result.put("raw", json);
        return result;
    }

    /** 探测当前 Key 是否可用（调用 models.list pageSize=1） */
    public Map<String, Object> probeKey(Long sourceId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            AiRuntimeConfig cfg = resolveGemini(sourceId);
            String key = resolveApiKey(cfg);
            String baseUrl = GeminiUtils.normalizeBaseUrl(cfg != null ? cfg.getBaseUrl() : null);
            result.put("source", briefSource(cfg, key));
            result.put("baseUrl", baseUrl);
            String raw = GeminiUtils.listModels(key, baseUrl, 1, null);
            JSONObject json = JSONObject.parseObject(raw);
            if (json == null) {
                throw new IllegalStateException("models.list 响应无法解析为 JSON");
            }
            JSONObject err = asJSONObject(json.get("error"));
            if(err != null) {
                result.put("ok", false);
                result.put("message", asString(err.get("message")));
                return result;
            }
            // if (json != null && json.getJSONObject("error") != null) {
            //     result.put("ok", false);
            //     result.put("message", json.getJSONObject("error").getString("message"));
            //     return result;
            // }
            int count = json != null && json.getJSONArray("models") != null ? json.getJSONArray("models").size() : 0;
            result.put("ok", true);
            result.put("message", "Key 可用，已成功访问 models.list");
            result.put("sampleModelCount", count);
            result.put("hasMore", StringUtils.isNotEmpty(json != null ? json.getString("nextPageToken") : null));
        } catch (Exception e) {
            log.warn("probeKey failed", e);
            result.put("ok", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * Google 侧能力边界说明（官方 Generative Language API 无法覆盖的操作）
     */
    public Map<String, Object> capabilities() {
        Map<String, Object> data = new LinkedHashMap<>();

        List<Map<String, String>> supported = new ArrayList<>();
        supported.add(cap("models.list", "GET /v1beta/models", "列出全部模型及 token/能力元数据", "本后台「模型列表」"));
        supported.add(cap("models.get", "GET /v1beta/models/{model}", "单个模型详情", "本后台「模型详情」"));
        supported.add(cap("generateContent", "POST ...:generateContent", "文本/多模态生成", "简历 AI / 聊天 / AI 源测试"));
        supported.add(cap("streamGenerateContent", "POST ...:streamGenerateContent", "流式生成", "聊天流式"));
        supported.add(cap("embedContent", "POST ...:embedContent", "向量嵌入（部分模型）", "可后续扩展"));
        data.put("apiSupported", supported);

        List<Map<String, String>> unsupported = new ArrayList<>();
        unsupported.add(cap("申请免费 Key", "无公开 API", "须在 Google AI Studio 网页创建",
                "https://aistudio.google.com/apikey"));
        unsupported.add(cap("查看免费档用量/剩余配额", "Generative Language API 无此接口",
                "仅能看 429 错误里的 RetryInfo；用量在 AI Studio Dashboard 或 Cloud Console",
                "https://ai.google.dev/gemini-api/docs/rate-limits"));
        unsupported.add(cap("付费 Key 用量账单", "需 GCP Billing / Monitoring（OAuth 服务账号）",
                "不能仅用 API Key 拉取；Cloud Quotas / Monitoring 属另一套 Google Cloud API",
                "https://console.cloud.google.com/apis/api/generativelanguage.googleapis.com/quotas"));
        unsupported.add(cap("在线创建/轮换 API Key", "需 AI Studio 或 API Keys API + Cloud IAM",
                "无法仅凭现有 Gemini API Key 自助签发新 Key",
                "https://ai.google.dev/gemini-api/docs/api-key"));
        data.put("apiUnsupported", unsupported);

        List<Map<String, String>> workarounds = new ArrayList<>();
        workarounds.add(cap("本后台可做", "ai_source + models.list + probe + 通用测试",
                "切换模型、验证 Key、浏览能力、用 GET/POST 实测 AI 源", "无需打开 Google 也可完成日常模型运维"));
        workarounds.add(cap("本地用量近似", "记录本系统调用日志",
                "可基于 ChatAILog / 业务日志统计调用次数（非官方配额）", "后续可加本地用量面板"));
        workarounds.add(cap("429 反馈", "错误体 RESOURCE_EXHAUSTED + retryDelay",
                "后端已解析为中文提示，可据此退避重试或换 Flash 模型", "无需进官网即可感知限流"));
        data.put("workarounds", workarounds);

        data.put("docs", Arrays.asList(
                "https://ai.google.dev/api/models",
                "https://ai.google.dev/gemini-api/docs",
                "https://ai.google.dev/gemini-api/docs/rate-limits",
                "https://ai.dev/rate-limit"
        ));
        data.put("summary", "可用 API Key 完成：模型浏览、详情、生成调用、Key 探测与 AI 源连通性测试。"
                + "不可用纯 API Key 完成：申请免费 Key、精确查看剩余配额/账单、在网页外签发新 Key。"
                + "付费用量需绑定 GCP 后走 Cloud Console / Monitoring（需服务账号）。");
        return data;
    }

    private AiRuntimeConfig resolveGemini(Long sourceId) {
        return aiSourceService.resolve(sourceId, "gemini", null);
    }

    /** 优先运行时配置，再回退 YAML ai.gemini.api-key */
    private String resolveApiKey(AiRuntimeConfig cfg) {
        if (cfg != null && StringUtils.isNotEmpty(cfg.getApiKey())) {
            return cfg.getApiKey().trim();
        }
        if (StringUtils.isNotEmpty(apiKey)) {
            return apiKey.trim();
        }
        throw new IllegalStateException("Gemini API Key 未配置，请在 YAML(ai.gemini.api-key) 或后台 AI 源中配置。");
    }

    private Map<String, Object> briefSource(AiRuntimeConfig cfg, String usedKey) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (cfg != null) {
            m.put("sourceId", cfg.getSourceId());
            m.put("sourceName", cfg.getSourceName());
            m.put("provider", cfg.getProvider());
            m.put("model", cfg.getModel());
            m.put("baseUrl", GeminiUtils.normalizeBaseUrl(cfg.getBaseUrl()));
        }
        if (StringUtils.isNotEmpty(usedKey) && usedKey.length() > 8) {
            m.put("apiKeyMasked", usedKey.substring(0, 4) + "****" + usedKey.substring(usedKey.length() - 4));
        } else {
            m.put("apiKeyMasked", StringUtils.isEmpty(usedKey) ? "(empty)" : "****");
        }
        return m;
    }

    /**
     * 映射官方 Model 资源字段：
     * name / baseModelId / version / displayName / description /
     * inputTokenLimit / outputTokenLimit / supportedGenerationMethods /
     * thinking / temperature / maxTemperature / topP / topK
     */
    private Map<String, Object> normalizeModel(JSONObject raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (raw == null) {
            return m;
        }
        String name = asString(raw.get("name"));
        String modelId = name;
        if (modelId != null && modelId.startsWith("models/")) {
            modelId = modelId.substring("models/".length());
        }
        String baseModelId = asString(raw.get("baseModelId"));
        if (StringUtils.isEmpty(baseModelId) && StringUtils.isNotEmpty(modelId)) {
            baseModelId = modelId;
        }

        // 只用 Map#get，避免 fastjson 2.x 的 getInteger/getBooleanValue 触发 TypeUtils
        // （会因缺少 IdentityHashMap 抛 NoClassDefFoundError）
        List<String> methods = toStringList(asJSONArray(raw.get("supportedGenerationMethods")));

        m.put("name", name);
        m.put("modelId", modelId);
        m.put("baseModelId", baseModelId);
        m.put("version", asString(raw.get("version")));
        m.put("displayName", asString(raw.get("displayName")));
        m.put("description", asString(raw.get("description")));
        m.put("inputTokenLimit", asInteger(raw.get("inputTokenLimit")));
        m.put("outputTokenLimit", asInteger(raw.get("outputTokenLimit")));
        m.put("supportedGenerationMethods", methods);
        m.put("thinking", raw.containsKey("thinking") ? asBoolean(raw.get("thinking")) : null);
        m.put("temperature", raw.get("temperature"));
        m.put("maxTemperature", raw.get("maxTemperature"));
        m.put("topP", raw.get("topP"));
        m.put("topK", raw.get("topK"));
        m.put("supportsGenerateContent", methods.stream().anyMatch(x -> "generateContent".equalsIgnoreCase(x)));
        m.put("supportsEmbed", methods.stream().anyMatch(x -> "embedContent".equalsIgnoreCase(x)));
        m.put("supportsCountTokens", methods.stream().anyMatch(x ->
                "countTokens".equalsIgnoreCase(x) || "countTextTokens".equalsIgnoreCase(x)));
        m.put("tierHint", guessTierHint(modelId));
        return m;
    }

    private List<String> toStringList(JSONArray arr) {
        List<String> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.size(); i++) {
            String s = asString(arr.get(i));
            if (StringUtils.isNotEmpty(s)) {
                list.add(s);
            }
        }
        return list;
    }

    private static String asString(Object val) {
        return val == null ? null : String.valueOf(val);
    }

    private static Integer asInteger(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Integer) {
            return (Integer) val;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception e) {
            return null;
        }
    }

    private static Boolean asBoolean(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return Boolean.parseBoolean(String.valueOf(val));
    }

    /** 避免 fastjson 2.x getJSONObject(null/缺失) 抛 TODO */
    @SuppressWarnings("unchecked")
    private static JSONObject asJSONObject(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof JSONObject) {
            return (JSONObject) val;
        }
        if (val instanceof Map) {
            return new JSONObject((Map<String, Object>) val);
        }
        if (val instanceof String) {
            return JSONObject.parseObject((String) val);
        }
        return null;
    }

    /** 避免 fastjson 2.x getJSONArray(null/缺失) 抛 TODO */
    @SuppressWarnings("unchecked")
    private static JSONArray asJSONArray(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof JSONArray) {
            return (JSONArray) val;
        }
        if (val instanceof List) {
            return new JSONArray((List<Object>) val);
        }
        if (val instanceof String) {
            return JSONArray.parseArray((String) val);
        }
        return null;
    }

    private String guessTierHint(String modelId) {
        if (modelId == null) {
            return "";
        }
        String id = modelId.toLowerCase(Locale.ROOT);
        if (id.contains("flash-lite")) {
            return "免费档友好 / 高性价比";
        }
        if (id.contains("flash") && !id.contains("pro")) {
            return "免费档推荐";
        }
        if (id.contains("pro")) {
            return "Pro：免费配额常极低或为 0，建议付费";
        }
        if (id.contains("embedding")) {
            return "嵌入模型";
        }
        if (id.contains("imagen") || id.contains("veo")) {
            return "图片/视频生成";
        }
        if (id.contains("tts")) {
            return "语音合成";
        }
        return "通用";
    }

    @SuppressWarnings("unchecked")
    private boolean matchMethod(Map<String, Object> model, String methodFilter) {
        if (StringUtils.isEmpty(methodFilter)) {
            return true;
        }
        Object methods = model.get("supportedGenerationMethods");
        if (methods instanceof Collection) {
            for (Object item : (Collection<?>) methods) {
                if (methodFilter.equalsIgnoreCase(String.valueOf(item))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchKeyword(Map<String, Object> model, String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return true;
        }
        String k = keyword.toLowerCase(Locale.ROOT);
        return String.valueOf(model.get("modelId")).toLowerCase(Locale.ROOT).contains(k)
                || String.valueOf(model.get("baseModelId")).toLowerCase(Locale.ROOT).contains(k)
                || String.valueOf(model.get("displayName")).toLowerCase(Locale.ROOT).contains(k)
                || String.valueOf(model.get("description")).toLowerCase(Locale.ROOT).contains(k);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> buildMethodStats(List<Map<String, Object>> models) {
        Map<String, Integer> stats = new TreeMap<>();
        for (Map<String, Object> model : models) {
            Object methods = model.get("supportedGenerationMethods");
            if (!(methods instanceof Collection)) {
                continue;
            }
            for (Object item : (Collection<?>) methods) {
                String method = String.valueOf(item);
                stats.merge(method, 1, Integer::sum);
            }
        }
        return stats;
    }

    private Map<String, String> cap(String name, String api, String note, String action) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("api", api);
        m.put("note", note);
        m.put("action", action);
        return m;
    }
}
