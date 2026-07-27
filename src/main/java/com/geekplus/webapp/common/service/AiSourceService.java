package com.geekplus.webapp.common.service;

import com.geekplus.common.ai.AiRuntimeConfig;
import com.geekplus.common.ai.config.AiProperties;
import com.geekplus.common.util.google.GeminiUtils;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.webapp.common.entity.AiSource;
import com.geekplus.webapp.common.mapper.AiSourceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * AI 源管理 + 运行时解析（请求覆盖 > DB 默认 > YAML）
 */
@Slf4j
@Service
public class AiSourceService {

    private final AiSourceMapper aiSourceMapper;
    private final AiProperties aiProperties;

    public AiSourceService(AiSourceMapper aiSourceMapper, AiProperties aiProperties) {
        this.aiSourceMapper = aiSourceMapper;
        this.aiProperties = aiProperties;
    }

    public List<AiSource> listAll() {
        try {
            return Optional.ofNullable(aiSourceMapper.findAll()).orElse(Collections.emptyList());
        } catch (Exception e) {
            log.warn("读取 ai_source 失败，将仅使用 YAML 配置: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<AiSource> listEnabled() {
        try {
            return Optional.ofNullable(aiSourceMapper.findEnabled()).orElse(Collections.emptyList());
        } catch (Exception e) {
            log.warn("读取 ai_source 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public AiSource getById(Long id) {
        return aiSourceMapper.findById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int save(AiSource source) {
        Date now = new Date();
        source.setUpdatedAt(now);
        if (source.getEnabled() == null) {
            source.setEnabled(1);
        }
        if (source.getIsDefault() == null) {
            source.setIsDefault(0);
        }
        if (source.getSortOrder() == null) {
            source.setSortOrder(0);
        }
        if (source.getId() == null) {
            source.setCreatedAt(now);
            if (Objects.equals(source.getIsDefault(), 1)) {
                aiSourceMapper.clearDefault();
            }
            return aiSourceMapper.insert(source);
        }
        if (Objects.equals(source.getIsDefault(), 1)) {
            aiSourceMapper.clearDefault();
        }
        return aiSourceMapper.update(source);
    }

    @Transactional(rollbackFor = Exception.class)
    public int setDefault(Long id) {
        aiSourceMapper.clearDefault();
        return aiSourceMapper.setDefault(id);
    }

    public int delete(Long id) {
        return aiSourceMapper.deleteById(id);
    }

    /**
     * 解析本次调用使用的 AI 源。
     * 优先级：指定 sourceId > provider(+model) 匹配 DB > DB 默认 > YAML
     */
    public AiRuntimeConfig resolve(Long sourceId, String provider, String model) {
        AiSource dbSource = null;
        try {
            String normalizedProvider = StringUtils.isNotEmpty(provider)
                    ? provider.trim().toLowerCase(Locale.ROOT)
                    : null;
            if ("openai".equals(normalizedProvider)) {
                normalizedProvider = "chatgpt";
            }
            if (sourceId != null) {
                dbSource = aiSourceMapper.findById(sourceId);
                if (dbSource != null && !Objects.equals(dbSource.getEnabled(), 1)) {
                    dbSource = null;
                }
            }
            if (dbSource == null && StringUtils.isNotEmpty(normalizedProvider)) {
                dbSource = aiSourceMapper.findEnabledByProvider(normalizedProvider);
            }
            if (dbSource == null && StringUtils.isEmpty(normalizedProvider)) {
                dbSource = aiSourceMapper.findDefault();
            }
        } catch (Exception e) {
            log.warn("解析 ai_source 异常，回退 YAML: {}", e.getMessage());
        }

        if (dbSource != null) {
            String p = dbSource.getProvider().toLowerCase(Locale.ROOT);
            if ("openai".equals(p)) {
                p = "chatgpt";
            }
            String m = StringUtils.isNotEmpty(model) ? model : dbSource.getModel();
            return mergeWithYaml(p, m, dbSource.getApiKey(), dbSource.getApiUrl(), dbSource.getId(), dbSource.getName());
        }

        String p = StringUtils.isNotEmpty(provider)
                ? provider.trim().toLowerCase(Locale.ROOT)
                : Optional.ofNullable(aiProperties.getDefaultProvider()).orElse("gemini").toLowerCase(Locale.ROOT);
        if ("openai".equals(p)) {
            p = "chatgpt";
        }
        return mergeWithYaml(p, model, null, null, null, "yaml-default");
    }

    private AiRuntimeConfig mergeWithYaml(String provider, String model, String apiKey, String apiUrl,
                                          Long sourceId, String sourceName) {
        String p = provider == null ? "gemini" : provider.trim().toLowerCase(Locale.ROOT);
        if ("openai".equals(p)) {
            p = "chatgpt";
        }

        // ChatGPT / OpenAI 官方
        if ("chatgpt".equals(p)) {
            AiProperties.Chatgpt c = aiProperties.getChatgpt();
            return AiRuntimeConfig.builder()
                    .provider("chatgpt")
                    .model(StringUtils.isNotEmpty(model) ? model : c.getModel())
                    .apiKey(StringUtils.isNotEmpty(apiKey) ? apiKey : c.getApiKey())
                    .apiUrl(StringUtils.isNotEmpty(apiUrl) ? apiUrl : c.getApiUrl())
                    .baseUrl(null)
                    .sourceId(sourceId)
                    .sourceName(sourceName)
                    .build();
        }

        // Google Gemini
        if ("gemini".equals(p)) {
            AiProperties.Gemini g = aiProperties.getGemini();
            String resolvedModel = StringUtils.isNotEmpty(model) ? model : g.getModel();
            String baseUrl = StringUtils.isNotEmpty(apiUrl) ? apiUrl : g.getBaseUrl();
            baseUrl = GeminiUtils.normalizeBaseUrl(baseUrl);
            return AiRuntimeConfig.builder()
                    .provider("gemini")
                    .model(resolvedModel)
                    .apiKey(StringUtils.isNotEmpty(apiKey) ? apiKey : g.getApiKey())
                    .apiUrl(GeminiUtils.buildGenerateUrl(baseUrl, resolvedModel))
                    .baseUrl(baseUrl)
                    .sourceId(sourceId)
                    .sourceName(sourceName)
                    .build();
        }

        // 其它自定义 / 国内提供方：走通用 OpenAI 兼容协议（GenericAiService / ChatGPTProvider）
        return AiRuntimeConfig.builder()
                .provider(p)
                .model(StringUtils.isNotEmpty(model) ? model : "gpt-4o-mini")
                .apiKey(apiKey)
                .apiUrl(apiUrl)
                .baseUrl(null)
                .sourceId(sourceId)
                .sourceName(sourceName)
                .build();
    }

    /** 推荐免费模型清单（供后台下拉） */
    public List<Map<String, String>> recommendedFreeModels() {
        List<Map<String, String>> list = new ArrayList<>();
        for (String m : GeminiUtils.FREE_TIER_MODELS) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("provider", "gemini");
            item.put("model", m);
            item.put("remark", "Gemini 免费档 REST generateContent");
            list.add(item);
        }
        Map<String, String> chatgpt = new LinkedHashMap<>();
        chatgpt.put("provider", "chatgpt");
        chatgpt.put("model", "gpt-4o-mini");
        chatgpt.put("remark", "需付费/自有 Key");
        list.add(chatgpt);
        return list;
    }
}
