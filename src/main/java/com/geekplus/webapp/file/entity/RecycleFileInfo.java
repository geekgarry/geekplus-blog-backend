package com.geekplus.webapp.file.entity;

import lombok.Data;

import java.util.Date;

/**
 * author     : geekplus
 * email      :
 * date       : 3/27/26 9:38 AM
 * description: //回收站专用的 DTO 实体类
 */
@Data
public class RecycleFileInfo {
    private String recycleName;  // 在回收站中的实际文件名 (包含时间戳和Base64)
    private String originalName; // 原文件名
    private String originalPath; // 原路径
    private Long size;           // 大小
    private Date deleteTime;     // 删除时间
    private Boolean isDirectory; // 是否是文件夹
}
