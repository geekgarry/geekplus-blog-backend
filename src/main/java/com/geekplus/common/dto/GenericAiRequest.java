package com.geekplus.common.dto;

import lombok.Data;

/**
 * 通用 AI 请求 / AI 源连通性测试入参。
 */
@Data
public class GenericAiRequest {
    /** 已保存的 AI 源 id（优先） */
    private Long sourceId;
    /** 提供方：gemini / chatgpt（无 sourceId 时用） */
    private String provider;
    private String model;
    /** 覆盖 API Key（测试未保存的表单时可传） */
    private String apiKey;
    /**
     * 覆盖接口地址：
     * - gemini：baseUrl 或完整 generateContent URL
     * - chatgpt：chat/completions 完整 URL
     */
    private String apiUrl;
    /** GET / POST，默认 POST */
    private String method = "POST";
    /** 提示词或请求正文（POST 时作为用户消息；也可传原始 JSON） */
    private String prompt;
    /** 为 true 时仅预览 URL/Key/Body，不真正发请求 */
    private Boolean previewOnly;
}
