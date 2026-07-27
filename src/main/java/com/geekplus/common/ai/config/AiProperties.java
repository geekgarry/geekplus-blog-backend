package com.geekplus.common.ai.config;

import com.geekplus.common.util.google.GeminiUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 全局配置（YAML）。运行时可被 ai_source 表覆盖。
 * provider 与 gemini / chatgpt 命名对齐；ChatGPT 请求 URL 仍为 api.openai.com。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 默认提供方：gemini / chatgpt */
    private String defaultProvider = "gemini";

    private Gemini gemini = new Gemini();
    private Chatgpt chatgpt = new Chatgpt();

    @Data
    public static class Gemini {
        private String apiKey = "";
        private String baseUrl = GeminiUtils.DEFAULT_BASE_URL;
        /** 同步 generateContent 默认模型（免费档推荐 flash） */
        private String model = GeminiUtils.DEFAULT_MODEL;
        private String streamModel = GeminiUtils.DEFAULT_MODEL;
        private String historyModel = GeminiUtils.DEFAULT_MODEL;
        private String ttsModel = "gemini-2.5-flash-preview-tts";
    }

    @Data
    public static class Chatgpt {
        private String apiKey = "";
        /** OpenAI 官方 Chat Completions 地址（域名 openai 不变） */
        private String apiUrl = "https://api.openai.com/v1/chat/completions";
        private String model = "gpt-4o-mini";
    }
}
