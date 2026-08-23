package com.geekplus.webapp.tool.skeleton.dto;

import lombok.Data;

/**
 * P2：按 URL 抓取页面 HTML（后端代理，规避浏览器跨域）。
 */
@Data
public class SkeletonFetchRequest {
    /** 完整 http(s) 地址 */
    private String url;
}
