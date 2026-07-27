package com.geekplus.common.ai;

import com.geekplus.common.domain.ChatPrompt;
import com.geekplus.common.dto.AIRequest;
import com.geekplus.common.util.google.GeminiUtils;
import com.geekplus.common.util.string.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Google Gemini（Generative Language REST）
 * 文档：https://ai.google.dev/gemini-api/docs
 */
@Slf4j
@Component
public class GeminiProvider implements AIProvider {

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public String generate(AIRequest request, AiRuntimeConfig config) {
        ChatPrompt prompt = new ChatPrompt();
        prompt.setChatMsg(buildPrompt(request));
        return chat(prompt, config);
    }

    @Override
    public String chat(ChatPrompt chatPrompt, AiRuntimeConfig config) {
        if (config == null || StringUtils.isEmpty(config.getApiKey())) {
            return "Gemini API Key 未配置，请在 YAML(ai.gemini.api-key) 或后台 AI 源中配置。";
        }
        try {
            String model = StringUtils.isNotEmpty(config.getModel()) ? config.getModel() : GeminiUtils.DEFAULT_MODEL;
            if (chatPrompt.getHistoryChatData() == null) {
                return GeminiUtils.postGemini(chatPrompt, config.getApiKey(), model, config.getBaseUrl());
            }
            return GeminiUtils.postGeminiHistory(chatPrompt, config.getApiKey(), model, config.getBaseUrl());
        } catch (Exception e) {
            log.error("Gemini 调用失败", e);
            return "Gemini 调用失败：" + e.getMessage();
        }
    }

    private String buildPrompt(AIRequest request) {
        StringBuilder prompt = new StringBuilder();
        if (StringUtils.isNotEmpty(request.getPrompt())) {
            prompt.append(request.getPrompt());
            return prompt.toString();
        }
        prompt.append("请根据以下信息生成或优化简历内容：\n");
        prompt.append("操作类型：").append(request.getAction()).append("\n");
        if (request.getTemplateKey() != null) {
            prompt.append("模板类型：").append(request.getTemplateKey()).append("\n");
        }
        prompt.append("当前简历数据：").append(request.getResumeData()).append("\n");
        prompt.append("请输出可直接使用的专业简历内容。\n");
        return prompt.toString();
    }
}
