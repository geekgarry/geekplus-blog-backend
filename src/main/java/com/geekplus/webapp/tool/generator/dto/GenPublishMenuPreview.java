package com.geekplus.webapp.tool.generator.dto;

import lombok.Data;

/**
 * 发布菜单预览：仅基于 gen_table 元数据，不校验磁盘文件（代码请用 ZIP 下载后手工合并）。
 */
@Data
public class GenPublishMenuPreview {

    private Long tableId;

    private String tableName;

    private String moduleName;

    private String businessName;

    /** 将写入 sys_menu.component，对应 @/views/admin/{component} */
    private String component;

    private String permissionPrefix;

    private String suggestedMenuName;

    private String suggestedPath;

    /** 是否已存在同 component 菜单（幂等提示） */
    private boolean alreadyPublished;

    private Long existingMainMenuId;
}
