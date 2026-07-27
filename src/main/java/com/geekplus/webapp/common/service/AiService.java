package com.geekplus.webapp.common.service;

import com.geekplus.common.ai.AIProvider;
import com.geekplus.common.ai.AiRuntimeConfig;
import com.geekplus.common.domain.ChatPrompt;
import com.geekplus.common.dto.AIRequest;
import com.geekplus.common.util.string.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 统一 AI 门面：按配置的 AI 源选择具体 {@link AIProvider}。
 * 后续新增提供方：实现 AIProvider + @Component 即可自动注册。
 */
@Slf4j
@Service
public class AiService {

    private final Map<String, AIProvider> providers;
    private final AiSourceService aiSourceService;

    public AiService(List<AIProvider> providerList, AiSourceService aiSourceService) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        p -> p.providerName().toLowerCase(Locale.ROOT),
                        p -> p,
                        (a, b) -> a));
        this.aiSourceService = aiSourceService;
    }

    public Set<String> availableProviders() {
        return providers.keySet();
    }

    public String generate(AIRequest request) {
        AiRuntimeConfig config = aiSourceService.resolve(
                request.getSourceId(),
                request.getProvider(),
                request.getModel());
        AIProvider provider = requireProvider(config.getProvider());
        log.info("AI generate via provider={} model={} source={}",
                config.getProvider(), config.getModel(), config.getSourceName());
        return provider.generate(request, config);
    }

    /**
     * 聊天入口（简历 AI / 可逐步替代 GeminiChatService 直连）
     */
    public String chat(ChatPrompt chatPrompt) {
        AiRuntimeConfig config = aiSourceService.resolve(
                chatPrompt.getSourceId(),
                chatPrompt.getProvider(),
                chatPrompt.getModel());
        AIProvider provider = requireProvider(config.getProvider());
        log.info("AI chat via provider={} model={} source={}",
                config.getProvider(), config.getModel(), config.getSourceName());
        String text = provider.chat(chatPrompt, config);
        if (looksLikeProviderError(text)) {
            throw new IllegalStateException(text);
        }
        return text;
    }

    public AiRuntimeConfig resolveConfig(String provider, String model) {
        return aiSourceService.resolve(null, provider, model);
    }

    /** 配额/鉴权等业务失败时 Provider 常返回中文说明而非抛异常 */
    private boolean looksLikeProviderError(String text) {
        if (StringUtils.isEmpty(text)) {
            return true;
        }
        String t = text.trim();
        return t.contains("配额不足")
                || t.contains("请求过于频繁")
                || t.contains("未配置")
                || t.contains("API Key")
                || t.startsWith("Gemini 调用失败")
                || t.startsWith("ChatGPT 调用失败")
                || t.startsWith("ChatGPT 请求失败")
                || t.startsWith("Gemini 服务暂时不可用")
                || t.startsWith("未找到 AI 提供方");
    }

    private AIProvider requireProvider(String providerKey) {
        String key = StringUtils.isEmpty(providerKey) ? "gemini" : providerKey.toLowerCase(Locale.ROOT);
        // 兼容旧编码 openai → chatgpt
        if ("openai".equals(key)) {
            key = "chatgpt";
        }
        AIProvider provider = providers.get(key);
        if (provider != null) {
            return provider;
        }
        // 非 gemini/chatgpt：走通用 OpenAI 兼容通道（复用 chatgpt 实现）
        AIProvider compatible = providers.get("chatgpt");
        if (compatible == null) {
            throw new IllegalArgumentException(
                    String.format("未找到 AI 提供方 '%s'，已注册：%s。自定义提供方需要 chatgpt 兼容实现。",
                            key, providers.keySet()));
        }
        log.info("自定义提供方 '{}' 走通用 AI 服务（OpenAI 兼容）", key);
        return compatible;
    }
}
