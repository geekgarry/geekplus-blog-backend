package com.geekplus.webapp.tool.resume.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class ResumeData implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String title;
    /** 模板标识（对应列 template_key，兼容无列时可为 null） */
    private String templateKey;
    private String dataJson;
    private Date updatedAt;
    private Date createdAt;
}
