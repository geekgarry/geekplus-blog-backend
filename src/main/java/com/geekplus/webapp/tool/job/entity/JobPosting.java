package com.geekplus.webapp.tool.job.entity;

import lombok.Data;

import java.util.Date;

/**
 * 岗位库表 job_posting：爬虫/公开 API 入库后的结构化岗位。
 */
@Data
public class JobPosting {
    private Long id;
    /** 来源平台编码：remotive / manual / … */
    private String source;
    /** 来源侧唯一键，用于幂等 upsert */
    private String sourceId;
    private String title;
    private String company;
    private String city;
    private String salary;
    private String summary;
    private String requirements;
    private String url;
    private String industry;
    private String tags;
    private Date fetchedAt;
    private Date expireAt;
    private Date createdAt;
    private Date updatedAt;
}
