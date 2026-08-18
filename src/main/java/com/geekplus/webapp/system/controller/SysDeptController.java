package com.geekplus.webapp.system.controller;

import com.geekplus.common.annotation.DataScope;
import com.geekplus.common.annotation.RepeatSubmit;
import com.geekplus.common.core.controller.BaseController;
import com.geekplus.common.datascope.DataPermissionHelper;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.domain.Result;
import com.geekplus.webapp.system.entity.SysDept;
import com.geekplus.webapp.system.service.SysDeptService;
import com.geekplus.webapp.system.service.SysRoleDeptService;
import com.geekplus.common.page.PageDataInfo;
import com.github.pagehelper.PageInfo;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;

/**
 * 部门表接口。
 * <p>
 * 列表走 {@code @DataScope} SQL 过滤；树接口用 {@link DataPermissionHelper} 裁剪并保留祖先路径，
 * 供用户管理筛选、选部门、角色「自定数据权限」勾选。
 */
@RestController
@RequestMapping("/sys/dept")
public class SysDeptController extends BaseController {
    @Resource
    private SysDeptService sysDeptService;
    @Resource
    private SysRoleDeptService sysRoleDeptService;
    @Resource
    private DataPermissionHelper dataPermissionHelper;

    /**
     * 增加 部门表
     */
    @RequiresPermissions("system:dept:add")
    @PostMapping("/add")
    @RepeatSubmit
    public Result add(@RequestBody SysDept sysDept) {
        Result deny = assertCanAccessDept(sysDept == null ? null : sysDept.getParentId());
        if (deny != null) {
            return deny;
        }
        return toResult(sysDeptService.insertSysDept(sysDept));
    }

    /**
     * 增加 部门表
     */
    @RequiresPermissions("system:dept:batchAdd")
    @PostMapping("/batchAdd")
    public Result batchAdd(@RequestBody List<SysDept> sysDept) {
    return toResult(sysDeptService.batchInsertSysDeptList(sysDept));
    }

    /**
     * 删除 部门表
     */
    @RequiresPermissions("system:dept:delete")
    @GetMapping("/delete")
    public Result removeSysDept(@RequestParam Long deptId) {
        Result deny = assertCanAccessDept(deptId);
        if (deny != null) {
            return deny;
        }
        return toResult(sysDeptService.deleteSysDeptById(deptId));
    }

    /**
     * 批量删除 部门表
     */
    @RequiresPermissions("system:dept:delete")
    @DeleteMapping("/{deptId}")
    public Result remove(@PathVariable Long deptId) {
        Result deny = assertCanAccessDept(deptId);
        if (deny != null) {
            return deny;
        }
        return toResult(sysDeptService.deleteSysDeptById(deptId));
    }

    /**
     * 更新 部门表
     */
    @RequiresPermissions("system:dept:update")
    @PostMapping("/update")
    public Result edit(@RequestBody SysDept sysDept) {
        if (sysDept != null) {
            Result deny = assertCanAccessDept(sysDept.getDeptId());
            if (deny != null) {
                return deny;
            }
            if (sysDept.getParentId() != null) {
                Result denyParent = assertCanAccessDept(sysDept.getParentId());
                if (denyParent != null) {
                    return denyParent;
                }
            }
        }
        return toResult(sysDeptService.updateSysDept(sysDept));
    }

    /**
     * 单条数据详情 部门表
     */
    @RequiresPermissions("system:dept:detail")
    @GetMapping("/detail")
    public Result detail(@RequestParam Long deptId) {
        Result deny = assertCanAccessDept(deptId);
        if (deny != null) {
            return deny;
        }
        SysDept sysDept = sysDeptService.selectSysDeptById(deptId);
        return Result.success(sysDept);
    }

    /**
     * 条件查询所有 部门表
     */
    @RequiresPermissions("system:dept:listAll")
    @DataScope(deptAlias = "sd")
    @GetMapping("/listAll")
    public PageDataInfo listAll(SysDept sysDept) {
        List<SysDept> list = sysDeptService.selectSysDeptList(sysDept);
        PageDataInfo rspData = new PageDataInfo();
        rspData.setCode(200);
        rspData.setMsg("查询成功");
        rspData.setRows(list);
        rspData.setTotal(new PageInfo(list).getTotal());
        return rspData;
    }

    /**
     * 条件查询所有 部门表
     */
    @RequiresPermissions("system:dept:list")
    @DataScope(deptAlias = "sd")
    @GetMapping("/list")
    public PageDataInfo list(SysDept sysDept) {
        startPage();
        List<SysDept> list = sysDeptService.selectSysDeptList(sysDept);
        return getDataTable(list);
    }

    /**
     * 部门树：用户管理左侧筛选、新增/编辑用户选部门、个人资料等。
     * 按当前登录者可见部门裁剪（管理员返回全树）。
     */
    @GetMapping("/getSysDeptTree")
    public Result getSysDeptTreeList() {
        List<SysDept> list = filterDeptTreeForCurrentUser(sysDeptService.getSysDeptTreeList());
        return Result.success(list);
    }

    /**
     * 角色数据权限：部门树 + 已勾选部门 ID。
     * 前端 roleDeptTreeselect 期望顶层字段 depts、checkedKeys；
     * 树同样按操作者可见范围裁剪，避免把看不见的部门授权给角色。
     */
    @GetMapping("/roleDeptTreeselect/{roleId}")
    public Result roleDeptTreeselect(@PathVariable("roleId") Long roleId) {
        List<SysDept> depts = filterDeptTreeForCurrentUser(sysDeptService.getSysDeptTreeList());
        Result ajax = Result.success();
        ajax.put("depts", depts);
        ajax.put("checkedKeys", sysRoleDeptService.selectDeptIdsByRoleId(roleId));
        return ajax;
    }

    /** 非豁免用户：只保留 data_scope 允许的部门及其祖先路径 */
    private List<SysDept> filterDeptTreeForCurrentUser(List<SysDept> fullTree) {
        LoginUser loginUser = getLoginUser();
        Set<Long> visible = dataPermissionHelper.resolveVisibleDeptIds(loginUser);
        return dataPermissionHelper.filterDeptTree(fullTree, visible);
    }

    /** 单条部门读写鉴权 */
    private Result assertCanAccessDept(Long deptId) {
        if (deptId == null) {
            return null;
        }
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            return Result.error("未登录");
        }
        if (!dataPermissionHelper.canAccessDept(loginUser, deptId)) {
            return Result.error("没有权限操作该部门数据");
        }
        return null;
    }
}
