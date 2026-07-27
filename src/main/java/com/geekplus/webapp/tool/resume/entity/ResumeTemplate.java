package com.geekplus.webapp.tool.resume.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class ResumeTemplate implements Serializable {
    private Long id;
    private String key;
    private String name;
    private String description;
    private String layoutJson;
    private Date updatedAt;
    private Date createdAt;
}
