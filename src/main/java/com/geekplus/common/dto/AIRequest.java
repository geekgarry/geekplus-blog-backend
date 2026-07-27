package com.geekplus.common.dto;

import lombok.Data;

/**
 * author     : geekplus
 * email      :
 * date       : 5/18/26 7:13 PM
 * description: //TODO
 */
@Data
public class AIRequest {
    /** 可选：指定 ai_source.id */
    private Long sourceId;
    /** gemini / chatgpt，空则用默认源 */
    private String provider;
    /** 可选覆盖模型 */
    private String model;
    private String action;
    private String prompt;
    private Object resumeData;
    private String templateKey;
}
