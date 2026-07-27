package com.geekplus.webapp.tool.resume.dto;

import lombok.Data;

@Data
public class ResumeSaveRequest {
    /** 已有简历 id：有则更新，无则新增一份 */
    private Long id;
    private String title;
    /** 模板 id，如 template1 */
    private String templateId;
    private Object data;
}
