package com.geekplus.common.ai;

import lombok.Builder;
import lombok.Data;

/**
 * 一次 AI 调用解析后的运行时配置（来自请求覆盖 / DB / YAML）
 */
@Data
@Builder
public class AiRuntimeConfig {
    private String provider;
    private String model;
    private String apiKey;
    /** ChatGPT 完整 chat/completions URL；Gemini 可为空 */
    private String apiUrl;
    /** Gemini baseUrl，如 https://generativelanguage.googleapis.com/v1beta */
    private String baseUrl;
    private Long sourceId;
    private String sourceName;
}
