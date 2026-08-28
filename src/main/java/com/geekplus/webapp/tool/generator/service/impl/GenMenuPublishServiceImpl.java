package com.geekplus.webapp.tool.generator.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.geekplus.webapp.system.entity.SysMenu;
import com.geekplus.webapp.system.entity.SysRoleMenu;
import com.geekplus.webapp.system.mapper.SysMenuMapper;
import com.geekplus.webapp.system.mapper.SysRoleMenuMapper;
import com.geekplus.webapp.system.service.SysMenuService;
import com.geekplus.webapp.system.service.SysRoleMenuService;
import com.geekplus.webapp.tool.generator.dto.GenPublishMenuPreview;
import com.geekplus.webapp.tool.generator.dto.GenPublishMenuRequest;
import com.geekplus.webapp.tool.generator.dto.GenPublishMenuResult;
import com.geekplus.webapp.tool.generator.entity.TableInfo;
import com.geekplus.webapp.tool.generator.service.GenCodeService;
import com.geekplus.webapp.tool.generator.service.GenMenuPublishService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 发布菜单：幂等（component）、事务（主菜单+按钮）、不读写前端工程磁盘。
 * 代码生成仍走 ZIP；开发者合并代码后自行发布菜单。
 */
@Slf4j
@Service
public class GenMenuPublishServiceImpl implements GenMenuPublishService {

    @Resource
    private GenCodeService genCodeService;
    @Resource
    private SysMenuService sysMenuService;
    @Resource
    private SysMenuMapper sysMenuMapper;
    @Resource
    private SysRoleMenuService sysRoleMenuService;
    @Resource
    private SysRoleMenuMapper sysRoleMenuMapper;

    @Override
    public GenPublishMenuPreview previewPublish(Long tableId) {
        TableInfo table = loadTable(tableId);
        GenPublishMenuPreview preview = new GenPublishMenuPreview();
        preview.setTableId(table.getTableId());
        preview.setTableName(table.getTableName());
        preview.setModuleName(table.getModuleName());
        preview.setBusinessName(table.getBusinessName());
        preview.setComponent(buildMenuComponent(table));
        preview.setPermissionPrefix(buildPermissionPrefix(table));
        preview.setSuggestedMenuName(table.getFunctionName());
        preview.setSuggestedPath(table.getBusinessName());

        SysMenu existing = findMainMenuByComponent(preview.getComponent());
        if (existing != null) {
            preview.setAlreadyPublished(true);
            preview.setExistingMainMenuId(existing.getMenuId());
        }
        return preview;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenPublishMenuResult publishMenus(GenPublishMenuRequest request, String operator) {
        if (request == null || request.getTableId() == null) {
            throw new IllegalArgumentException("tableId 不能为空");
        }
        if (request.getParentMenuId() == null) {
            throw new IllegalArgumentException("parentMenuId 不能为空");
        }

        TableInfo table = loadTable(request.getTableId());
        GenPublishMenuResult result = new GenPublishMenuResult();
        result.setComponent(buildMenuComponent(table));
        result.setPermissionPrefix(buildPermissionPrefix(table));
        result.setModuleName(table.getModuleName());
        result.setBusinessName(table.getBusinessName());

        SysMenu existing = findMainMenuByComponent(result.getComponent());
        if (existing != null) {
            result.setAlreadyPublished(true);
            result.setMainMenuId(existing.getMenuId());
            result.setMenuIds(collectMenuTreeIds(existing.getMenuId()));
            result.setMessage("菜单已存在（component=" + result.getComponent() + "），未重复插入");
            assignRoleMenus(request.getRoleIds(), result.getMenuIds());
            return result;
        }

        String menuName = StringUtils.isNotBlank(request.getMenuName()) ? request.getMenuName() : table.getFunctionName();
        String path = StringUtils.isNotBlank(request.getPath()) ? request.getPath() : table.getBusinessName();
        String icon = StringUtils.isNotBlank(request.getIcon()) ? request.getIcon() : "table";
        String permissionPrefix = result.getPermissionPrefix();

        SysMenu mainMenu = new SysMenu();
        mainMenu.setMenuName(menuName);
        mainMenu.setParentId(request.getParentMenuId());
        mainMenu.setOrderNum(1);
        mainMenu.setPath(path);
        mainMenu.setComponent(result.getComponent());
        mainMenu.setIsFrame(1);
        mainMenu.setIsCache(0);
        mainMenu.setMenuType("M");
        mainMenu.setVisible(0);
        mainMenu.setStatus(0);
        mainMenu.setPerms(permissionPrefix + ":list");
        mainMenu.setIcon(icon);
        mainMenu.setCreateBy(operator);
        mainMenu.setRemark(menuName + "菜单（代码生成发布）");

        sysMenuService.insertSysMenu(mainMenu);
        Long mainMenuId = mainMenu.getMenuId();
        result.setMainMenuId(mainMenuId);
        result.getMenuIds().add(mainMenuId);

        List<SysMenu> buttons = buildButtonMenus(mainMenuId, menuName, permissionPrefix, operator);
        if (!buttons.isEmpty()) {
            sysMenuService.batchInsertSysMenuList(buttons);
            SysMenu childQuery = new SysMenu();
            childQuery.setParentId(mainMenuId);
            List<SysMenu> insertedChildren = sysMenuMapper.selectSysMenuList(childQuery);
            for (SysMenu child : insertedChildren) {
                result.getMenuIds().add(child.getMenuId());
            }
        }

        updatePublishRemark(table, mainMenuId, result.getComponent(), operator);
        assignRoleMenus(request.getRoleIds(), result.getMenuIds());
        result.setMessage("发布成功，主菜单 ID=" + mainMenuId);
        return result;
    }

    private TableInfo loadTable(Long tableId) {
        TableInfo tableInfo = genCodeService.selectGenTableById(tableId);
        if (tableInfo == null) {
            throw new IllegalArgumentException("gen_table 不存在: " + tableId);
        }
        return genCodeService.getTableInfoByGenTable(tableInfo);
    }

    private SysMenu findMainMenuByComponent(String component) {
        SysMenu query = new SysMenu();
        query.setComponent(component);
        List<SysMenu> list = sysMenuMapper.selectSysMenuList(query);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 与 sql.ftl、permission.js 约定：module/business/index.vue */
    private String buildMenuComponent(TableInfo table) {
        return table.getModuleName() + "/" + table.getBusinessName() + "/index.vue";
    }

    private String buildPermissionPrefix(TableInfo table) {
        return table.getModuleName() + ":" + table.getBusinessName();
    }

    private List<SysMenu> buildButtonMenus(Long parentId, String title, String permissionPrefix, String operator) {
        List<SysMenu> buttons = new ArrayList<>();
        buttons.add(buildButton(parentId, title + "查询", 1, permissionPrefix + ":info", operator));
        buttons.add(buildButton(parentId, title + "新增", 2, permissionPrefix + ":add", operator));
        buttons.add(buildButton(parentId, title + "修改", 3, permissionPrefix + ":update", operator));
        buttons.add(buildButton(parentId, title + "删除", 4, permissionPrefix + ":delete", operator));
        buttons.add(buildButton(parentId, title + "导出", 5, permissionPrefix + ":export", operator));
        return buttons;
    }

    private SysMenu buildButton(Long parentId, String name, int order, String perms, String operator) {
        SysMenu menu = new SysMenu();
        menu.setMenuName(name);
        menu.setParentId(parentId);
        menu.setOrderNum(order);
        menu.setPath("#");
        menu.setComponent("");
        menu.setIsFrame(1);
        menu.setMenuType("B");
        menu.setVisible(0);
        menu.setStatus(0);
        menu.setPerms(perms);
        menu.setIcon("#");
        menu.setCreateBy(operator);
        return menu;
    }

    private List<Long> collectMenuTreeIds(Long mainMenuId) {
        List<Long> ids = new ArrayList<>();
        ids.add(mainMenuId);
        SysMenu q = new SysMenu();
        q.setParentId(mainMenuId);
        for (SysMenu child : sysMenuMapper.selectSysMenuList(q)) {
            ids.add(child.getMenuId());
        }
        return ids;
    }

    private void assignRoleMenus(List<Long> roleIds, List<Long> menuIds) {
        if (roleIds == null || roleIds.isEmpty() || menuIds == null || menuIds.isEmpty()) {
            return;
        }
        List<SysRoleMenu> toInsert = new ArrayList<>();
        for (Long roleId : roleIds) {
            for (Long menuId : menuIds) {
                SysRoleMenu query = new SysRoleMenu();
                query.setRoleId(roleId);
                query.setMenuId(menuId);
                if (!sysRoleMenuMapper.selectSysRoleMenuList(query).isEmpty()) {
                    continue;
                }
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                toInsert.add(rm);
            }
        }
        if (!toInsert.isEmpty()) {
            sysRoleMenuService.batchInsertSysRoleMenuList(toInsert);
        }
    }

    private void updatePublishRemark(TableInfo table, Long mainMenuId, String component, String operator) {
        JSONObject publish = new JSONObject();
        publish.put("mainMenuId", mainMenuId);
        publish.put("component", component);
        publish.put("publishedBy", operator);
        publish.put("publishedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        JSONObject root = new JSONObject();
        root.put("codegenMenuPublish", publish);
        TableInfo update = new TableInfo();
        update.setTableId(table.getTableId());
        update.setRemark(root.toJSONString());
        genCodeService.updateGenTable(update);
    }
}
