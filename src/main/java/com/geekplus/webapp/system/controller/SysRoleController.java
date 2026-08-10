package com.geekplus.webapp.system.controller;

import com.geekplus.common.annotation.Log;
import com.geekplus.common.annotation.RepeatSubmit;
import com.geekplus.common.constant.HttpStatus;
import com.geekplus.common.core.controller.BaseController;
import com.geekplus.common.domain.Result;
import com.geekplus.common.enums.BusinessType;
import com.geekplus.common.enums.OperatorType;
import com.geekplus.common.page.PageDataInfo;
import com.geekplus.common.util.poi.ExcelUtil;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.framework.aspect.DataScopeAspect;
import com.geekplus.webapp.system.entity.SysMenu;
import com.geekplus.webapp.system.entity.SysRole;
import com.geekplus.webapp.system.entity.SysRoleDept;
import com.geekplus.webapp.system.service.SysMenuService;
import com.geekplus.webapp.system.service.SysRoleDeptService;
import com.geekplus.webapp.system.service.SysRoleService;
import com.github.pagehelper.PageInfo;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统角色表 系统角色表
 * Created by CodeGenerator on 2023/06/18.
 */
@RestController
@RequestMapping("/sys/role")
public class SysRoleController extends BaseController {
    @Resource
    private SysRoleService sysRoleService;
    @Resource
    private SysMenuService sysMenuService;
    @Resource
    private SysRoleDeptService sysRoleDeptService;

    @Resource
    private com.geekplus.webapp.system.service.cache.RbacCacheService rbacCacheService;

    /**
     * 增加 系统角色表
     */
    @RequiresPermissions("system:role:add")
    @Log(title = "角色管理",businessType = BusinessType.INSERT,operatorType = OperatorType.MANAGE,isSaveRequestData = false)
    @PostMapping("/add")
    @RepeatSubmit
    public Result add(@RequestBody SysRole sysRole) {
        Result r = toResult(sysRoleService.insertSysRole(sysRole));
        if (sysRole.getRoleId() != null) {
            rbacCacheService.evictRole(sysRole.getRoleId());
        }
        return r;
    }

    /**
    * 删除 系统角色表
    */
    @RequiresPermissions("system:role:delete")
    @Log(title = "角色管理",businessType = BusinessType.DELETE,operatorType = OperatorType.MANAGE,isSaveRequestData = false)
    @GetMapping("/delete")
    public Result remove(@RequestParam Long roleId) {
        Result r = toResult(sysRoleService.deleteSysRoleById(roleId));
        rbacCacheService.evictRole(roleId);
        return r;
    }

    /**
    * 批量删除 系统角色表
    */
    @RequiresPermissions("system:role:delete")
    @Log(title = "角色管理",businessType = BusinessType.DELETE,operatorType = OperatorType.MANAGE,isSaveRequestData = false)
    @DeleteMapping("/{roleIds}")
    public Result remove(@PathVariable Long[] roleIds) {
        Result r = toResult(sysRoleService.deleteSysRoleByIds(roleIds));
        if (roleIds != null) {
            for (Long id : roleIds) {
                rbacCacheService.evictRole(id);
            }
        }
        return r;
    }

    /**
    * 更新 系统角色表
    */
    @RequiresPermissions("system:role:update")
    @Log(title = "角色管理",businessType = BusinessType.UPDATE,operatorType = OperatorType.MANAGE,isSaveRequestData = false)
    @PostMapping("/update")
    public Result edit(@RequestBody SysRole sysRole) {
        Result r = toResult(sysRoleService.updateSysRole(sysRole));
        if (sysRole.getRoleId() != null) {
            rbacCacheService.evictRole(sysRole.getRoleId());
        }
        return r;
    }

    /**
    * 单条数据详情 系统角色表
    */
    @RequiresPermissions("system:role:detail")
    @GetMapping("/detail")
    public Result detail(@RequestParam Long roleId) {
        SysRole sysRole = sysRoleService.selectSysRoleById(roleId);
        List<SysMenu> sysMenuList=sysMenuService.getMenuTreeByRoleId(roleId);
        sysRole.setSysMenuList(sysMenuList);
        return Result.success(sysRole);
    }

    /**
    * 条件查询所有 系统角色表
    */
    @RequiresPermissions("system:role:listAll")
    @GetMapping("/listAll")
    public PageDataInfo listAll(SysRole sysRole) {
        //PageHelper.startPage(page, size);
        List<SysRole> list = sysRoleService.selectSysRoleList(sysRole);
        PageDataInfo rspData = new PageDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setMsg("查询成功");
        rspData.setRows(list);
        rspData.setTotal(new PageInfo(list).getTotal());
        //PageInfo pageInfo = new PageInfo(list);
        return rspData;
    }

    /**
    * 条件查询所有 系统角色表
    */
    @RequiresPermissions("system:role:list")
    @GetMapping("/list")
    public PageDataInfo list(SysRole sysRole) {
        startPage();
        List<SysRole> list = sysRoleService.selectSysRoleList(sysRole);
        //PageInfo pageInfo = new PageInfo(list);
        return getDataTable(list);
    }

    /**
     * 导出系统角色表
     */
    @RequiresPermissions("system:role:export")
    @Log(title = "导出系统角色表", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public Result export(SysRole sysRole)
    {
        List<SysRole> list = sysRoleService.selectSysRoleList(sysRole);
        ExcelUtil<SysRole> util = new ExcelUtil<SysRole>(SysRole.class);
        return util.exportExcel(list, "sysRole");
    }

    /**
     * 保存角色数据权限范围：更新 data_scope；自定权限时全量替换 sys_role_dept
     * body: { roleId, dataScope, deptIds: [] }
     */
    @RequiresPermissions("system:role:update")
    @Log(title = "角色数据权限", businessType = BusinessType.UPDATE, operatorType = OperatorType.MANAGE, isSaveRequestData = false)
    @PostMapping("/dataScope")
    @Transactional(rollbackFor = Exception.class)
    public Result dataScope(@RequestBody SysRole sysRole)
    {
        if (sysRole.getRoleId() == null)
        {
            return Result.error("角色ID不能为空");
        }
        // 统一成字符串，兼容前端传数字
        String dataScope = sysRole.getDataScope() == null ? null : String.valueOf(sysRole.getDataScope()).trim();
        sysRole.setDataScope(dataScope);

        SysRole update = new SysRole();
        update.setRoleId(sysRole.getRoleId());
        update.setDataScope(dataScope);
        int rows = sysRoleService.updateSysRole(update);

        // 始终先清关联；仅自定数据权限时回写勾选部门
        sysRoleDeptService.deleteByRoleId(sysRole.getRoleId());
        if (DataScopeAspect.DATA_SCOPE_CUSTOM.equals(dataScope) && StringUtils.isNotEmpty(sysRole.getDeptIds()))
        {
            List<SysRoleDept> list = new ArrayList<>();
            for (Long deptId : sysRole.getDeptIds())
            {
                if (deptId == null)
                {
                    continue;
                }
                SysRoleDept rd = new SysRoleDept();
                rd.setRoleId(sysRole.getRoleId());
                rd.setDeptId(deptId);
                list.add(rd);
            }
            sysRoleDeptService.batchInsert(list);
        }
        rbacCacheService.evictRole(sysRole.getRoleId());
        return toResult(rows);
    }
}
