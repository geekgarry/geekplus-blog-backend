package com.geekplus.webapp.file.entity;

import lombok.Data;

import java.util.Date;

/**
 * author     : geekplus
 * email      :
 * date       : 3/26/26 2:47 AM
 * description: //TODO
 */
@Data
public class FileInfo {
    private String name;          // 文件/文件夹名称
    private String path;          // 相对路径 (如 /images/logo.png)
    private String fullPath;      // 绝对路径
    private String url;           // 网络地址
    private Boolean isDirectory;  // 是否为文件夹
    private Long size;            // 文件大小 (字节)
    private String type;          // 文件类型/后缀
    private Date updateTime;      // 最后修改时间
}
