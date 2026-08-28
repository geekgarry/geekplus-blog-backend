package com.geekplus.webapp.tool.generator.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GenPublishMenuResult {

    /** 命中幂等：菜单已存在，未重复插入 */
    private boolean alreadyPublished;

    private Long mainMenuId;

    private String component;

    private String permissionPrefix;

    private String moduleName;

    private String businessName;

    private List<Long> menuIds = new ArrayList<>();

    private String message;
}
