package com.geekplus.webapp.common.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI 源配置（后台可增删改，用于切换提供方与模型）
 */
@Data
public class AiSource implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    /** 显示名称 */
    private String name;
    /** 提供方编码：gemini / chatgpt / 自定义 */
    private String provider;
    /** 模型名，如 gemini-2.5-flash、gpt-4o-mini */
    private String model;
    /** API Key（可空则回退到 YAML） */
    private String apiKey;
    /**
     * 接口地址：
     * - gemini: baseUrl，如 https://generativelanguage.googleapis.com/v1beta
     * - chatgpt: 完整 chat/completions URL（如 https://api.openai.com/v1/chat/completions）
     */
    private String apiUrl;
    /** 1 启用 0 停用 */
    private Integer enabled;
    /** 1 默认源（全局仅一个） */
    private Integer isDefault;
    private Integer sortOrder;
    private String remark;
    private Date createdAt;
    private Date updatedAt;
}
