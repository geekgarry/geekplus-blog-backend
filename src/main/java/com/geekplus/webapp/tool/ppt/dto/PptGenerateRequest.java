package com.geekplus.webapp.tool.ppt.dto;

import lombok.Data;

/**
 * PPT 生成请求：支持简历 / 纯文本 / 附件正文多种来源。
 */
@Data
public class PptGenerateRequest {
    /** resume | text | file（file 时配合 fileText 或 media） */
    private String sourceType;
    private Object resumeData;
    /** 用户手输大纲/正文 */
    private String text;
    /** 本地已抽取的文档正文 */
    private String fileText;
    private String fileName;
    private String mediaData;
    private String mediaMimeType;
    private String title;
    /** 附加风格要求，如「偏商务」「多图示意」 */
    private String prompt;
    /** paper | mist | dusk | ink | coral —— 主题色板名，写入 slides 默认色 */
    private String theme;
}
