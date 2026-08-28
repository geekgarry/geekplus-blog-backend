package com.geekplus.webapp.tool.generator.dto;

import lombok.Data;

import java.util.List;

/**
 * 代码生成表发布为后台菜单（ZIP 生成与发布分离；幂等：同 component 不重复插入）。
 */
@Data
public class GenPublishMenuRequest {

    private Long tableId;

    /** 挂载父菜单 ID，如「系统工具」 */
    private Long parentMenuId;

    /** 功能菜单显示名，默认 gen_table.functionName */
    private String menuName;

    /** 路由 path，默认 businessName */
    private String path;

    /** 菜单图标，默认 table */
    private String icon;

    /** 发布后自动授权的角色 ID（可选，已有关联则跳过） */
    private List<Long> roleIds;
}
