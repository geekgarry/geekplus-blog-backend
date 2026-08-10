package com.geekplus.webapp.system.controller;

import com.geekplus.common.annotation.Log;
import com.geekplus.common.annotation.RepeatSubmit;
import com.geekplus.common.constant.HttpStatus;
import com.geekplus.common.core.controller.BaseController;
import com.geekplus.common.domain.Result;
import com.geekplus.common.enums.BusinessType;
import com.geekplus.common.page.PageDataInfo;
import com.geekplus.webapp.system.entity.SysRoleMenu;
import com.geekplus.webapp.system.service.SysRoleMenuService;
import com.geekplus.webapp.system.service.cache.RbacCacheService;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色-菜单：变更后失效对应角色 RBAC 双重缓存（不再把大权限集写回会话）。
 */
@RestController
@RequestMapping("/sys/roleMenu")
public class SysRoleMenuController extends BaseController {
    @Resource
    private SysRoleMenuService sysRoleMenuService;
    @Resource
    private RbacCacheService rbacCacheService;

    private void evictByRoleMenu(SysRoleMenu rm) {
        if (rm != null && rm.getRoleId() != null) {
            rbacCacheService.evictRole(rm.getRoleId());
        }
    }

    private void evictByRoleMenus(List<SysRoleMenu> list) {
        if (list == null) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        for (SysRoleMenu rm : list) {
            if (rm != null && rm.getRoleId() != null) {
                ids.add(rm.getRoleId());
            }
        }
        rbacCacheService.evictRoles(ids);
    }

    @Log(title = "添加角色和菜单权限", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @RepeatSubmit
    public Result add(@RequestBody SysRoleMenu sysRoleMenu) {
        if (sysRoleMenuService.insertSysRoleMenu(sysRoleMenu) > 0) {
            evictByRoleMenu(sysRoleMenu);
            return Result.success();
        }
        return Result.error();
    }

    @Log(title = "批量添加角色和菜单权限", businessType = BusinessType.INSERT)
    @PostMapping("/batchAdd")
    @RepeatSubmit
    public Result batchAdd(@RequestBody List<SysRoleMenu> sysRoleMenu) {
        if (sysRoleMenuService.batchInsertSysRoleMenuList(sysRoleMenu) > 0) {
            evictByRoleMenus(sysRoleMenu);
            return Result.success();
        }
        return Result.error();
    }

    @Log(title = "删除角色和菜单权限", businessType = BusinessType.DELETE)
    @GetMapping("/delete")
    public Result remove(@RequestParam Long roleId) {
        Result r = toResult(sysRoleMenuService.deleteSysRoleMenuById(roleId));
        rbacCacheService.evictRole(roleId);
        return r;
    }

    @Log(title = "批量删除角色和菜单权限", businessType = BusinessType.DELETE)
    @DeleteMapping("/{roleIds}")
    public Result remove(@PathVariable Long[] roleIds) {
        Result r = toResult(sysRoleMenuService.deleteSysRoleMenuByIds(roleIds));
        if (roleIds != null) {
            for (Long id : roleIds) {
                rbacCacheService.evictRole(id);
            }
        }
        return r;
    }

    @Log(title = "修改角色和菜单权限", businessType = BusinessType.UPDATE)
    @PostMapping
    public Result edit(@RequestBody SysRoleMenu sysRoleMenu) {
        Result r = toResult(sysRoleMenuService.updateSysRoleMenu(sysRoleMenu));
        evictByRoleMenu(sysRoleMenu);
        return r;
    }

    @GetMapping("/{roleId}")
    public Result detail(@PathVariable Long roleId) {
        SysRoleMenu sysRoleMenu = sysRoleMenuService.selectSysRoleMenuById(roleId);
        return Result.success(sysRoleMenu);
    }

    @GetMapping("/list")
    public PageDataInfo list(SysRoleMenu sysRoleMenu) {
        startPage();
        List<SysRoleMenu> list = sysRoleMenuService.selectSysRoleMenuList(sysRoleMenu);
        return getDataTable(list);
    }

    @GetMapping("/listAll")
    public PageDataInfo listAll(SysRoleMenu sysRoleMenu) {
        List<SysRoleMenu> list = sysRoleMenuService.selectSysRoleMenuList(sysRoleMenu);
        PageDataInfo rspData = new PageDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setMsg("查询成功");
        rspData.setRows(list);
        rspData.setTotal(new PageInfo(list).getTotal());
        return rspData;
    }

    @Log(title = "删除角色菜单关联", businessType = BusinessType.DELETE)
    @PostMapping("/removeRoleMenu")
    public Result removeRoleMenu(@RequestBody SysRoleMenu sysRoleMenu) {
        if (sysRoleMenuService.deleteSysRoleMenu(sysRoleMenu) > 0) {
            evictByRoleMenu(sysRoleMenu);
            return Result.success();
        }
        return Result.error();
    }

    @Log(title = "批量删除角色菜单关联", businessType = BusinessType.DELETE)
    @PostMapping("/removeRoleMenuList")
    public Result removeRoleMenuList(@RequestBody List<SysRoleMenu> list) {
        if (sysRoleMenuService.batchDeleteSysRoleMenu(list) > 0) {
            evictByRoleMenus(list);
            return Result.success();
        }
        return Result.error();
    }
}
