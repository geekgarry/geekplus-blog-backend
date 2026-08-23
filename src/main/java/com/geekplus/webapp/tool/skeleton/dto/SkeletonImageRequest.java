package com.geekplus.webapp.tool.skeleton.dto;

import lombok.Data;

/**
 * P3：界面截图 / 设计图 → 骨架 Schema（走统一 AI）。
 */
@Data
public class SkeletonImageRequest {
    /** 图片 base64（可带或不带 dataURL 前缀） */
    private String mediaData;
    private String mediaMimeType;
    private String provider;
    private String model;
    private Long sourceId;
}
