package com.geekplus.webapp.tool.resume.dto;

import lombok.Data;

/**
 * 简历 AI 分析请求：岗位分析 / 人岗匹配 / 按岗优化生成。
 */
@Data
public class ResumeAnalyzeRequest {
    /** job | match | generate */
    private String type;
    /** JD 或行业描述文本 */
    private String text;
    /** 可选：JD 截图/PDF 的 dataURL 或纯 base64 */
    private String fileData;
    private String mimeType;
    /** match / generate 时需要当前简历 */
    private Object resumeData;
}
