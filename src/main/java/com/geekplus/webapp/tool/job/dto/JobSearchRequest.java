package com.geekplus.webapp.tool.job.dto;

import lombok.Data;

/**
 * 岗位搜索请求（从 resume.dto 迁出，归属 job 模块）。
 */
@Data
public class JobSearchRequest {
    private String keyword;
    private String industry;
    private String city;
    private String experience;
    /** 可选：用于补全求职目标 */
    private Object resumeData;
}
