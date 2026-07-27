package com.geekplus.common.ai;

import com.geekplus.common.domain.ChatPrompt;
import com.geekplus.common.dto.AIRequest;

/**
 * AI 提供方 SPI。新增源时实现本接口并注册为 Spring Bean 即可被 AiService 发现。
 */
public interface AIProvider {

    /** 唯一编码，如 gemini / chatgpt */
    String providerName();

    /** 结构化简历等场景 */
    String generate(AIRequest request, AiRuntimeConfig config);

    /** 与 ChatGPTService / GeminiChatService 一致的聊天入口 */
    String chat(ChatPrompt chatPrompt, AiRuntimeConfig config);
}
