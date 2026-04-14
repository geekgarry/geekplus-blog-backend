package com.geekplus.webapp.file.entity;

import lombok.Data;

import java.util.List;

/**
 * author     : geekplus
 * email      :
 * date       : 3/27/26 2:28 AM
 * description: //用于接收前端传来的批量操作参数
 */
@Data
public class BatchOperationReq {
    private List<String> paths;      // 源文件路径列表
    private String destPath;         // 目标文件夹路径
    private Boolean hardDelete;      // 是否彻底删除
    private String zipName;          // 压缩包名称
}
