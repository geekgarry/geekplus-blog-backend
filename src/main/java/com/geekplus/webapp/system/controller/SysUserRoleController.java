package com.geekplus.webapp.system.controller;

import com.geekplus.common.annotation.Log;
import com.geekplus.common.annotation.RepeatSubmit;
import com.geekplus.common.constant.HttpStatus;
import com.geekplus.common.core.controller.BaseController;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.domain.Result;
import com.geekplus.common.enums.BusinessType;
import com.geekplus.common.enums.OperatorType;
import com.geekplus.common.page.PageDataInfo;
import com.geekplus.common.util.http.ServletUtil;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.webapp.common.service.SysUserTokenService;
import com.geekplus.webapp.system.entity.SysUser;
import com.geekplus.webapp.system.entity.SysUserRole;
import com.geekplus.webapp.system.service.SysUserRoleService;
import com.geekplus.webapp.system.service.SysUserService;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 系统用户角色关系表 系统用户角色关系表
 * Created by CodeGenerator on 2023/06/18.
 */
@RestController
@RequestMapping("/sys/userRole")
public class SysUserRoleController extends BaseController {
    @Resource
    private SysUserRoleService sysUserRoleService;
    @Resource
    private SysUserService sysUserService;
    @Resource
    private SysUserTokenService sysUserTokenService;

    /**
     * 增加 系统用户角色关系表
     */
    @Log(title = "添加用户和角色",businessType = BusinessType.INSERT,operatorType = OperatorType.MANAGE)
    @PostMapping("/add")
    @RepeatSubmit
    public Result add(@RequestBody SysUserRole sysUserRole) {
        if(sysUserRoleService.insertSysUserRole(sysUserRole)>0){
            refreshOrKickUserSession(sysUserRole.getUserId());
            return Result.success();
        }else {
            return Result.error();
        }
    }

    /**
     * 增加 系统用户角色关系表
     */
    @Log(title = "添加用户和角色",businessType = BusinessType.INSERT)
    @PostMapping("/batchAdd")
    @RepeatSubmit
    public Result batchAdd(@RequestBody List<SysUserRole> sysUserRole) {
        if(sysUserRoleService.batchInsertSysUserRoleList(sysUserRole)>0){
            if (sysUserRole != null) {
                for (SysUserRole ur : sysUserRole) {
                    if (ur != null) {
                        refreshOrKickUserSession(ur.getUserId());
                    }
                }
            }
            return Result.success();
        }else {
            return Result.error();
        }
    }

    /**
     * 删除 系统用户角色关系表
     */
    @Log(title = "删除用户和角色",businessType = BusinessType.DELETE)
    @GetMapping("/delete")
    public Result remove(@RequestParam Long userId) {
        return toResult(sysUserRoleService.deleteSysUserRoleById(userId));
    }

    /**
     * 批量删除 系统用户角色关系表
     */
    @Log(title = "批量删除用户和角色",businessType = BusinessType.DELETE)
    @DeleteMapping("/{userIds}")
    public Result remove(@PathVariable Long[] userIds) {
        return toResult(sysUserRoleService.deleteSysUserRoleByIds(userIds));
    }

    /**
     * 更新 系统用户角色关系表
     */
    @Log(title = "更新用户和角色",businessType = BusinessType.UPDATE)
    @PostMapping("/update")
    public Result edit(@RequestBody SysUserRole sysUserRole) {
        return toResult(sysUserRoleService.updateSysUserRole(sysUserRole));
    }

    /**
     * 单条数据详情 系统用户角色关系表
     */
    @GetMapping("/detail")
    public Result detail(@RequestParam Long userId) {
        SysUserRole sysUserRole = sysUserRoleService.selectSysUserRoleById(userId);
        return Result.success(sysUserRole);
    }

    /**
    * 条件查询所有 系统用户角色关系表
    */
    @GetMapping("/listAll")
    public PageDataInfo listAll(SysUserRole sysUserRole) {
        //PageHelper.startPage(page, size);
        List<SysUserRole> list = sysUserRoleService.selectSysUserRoleList(sysUserRole);
        PageDataInfo rspData = new PageDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setMsg("查询成功");
        rspData.setRows(list);
        rspData.setTotal(new PageInfo(list).getTotal());
        //PageInfo pageInfo = new PageInfo(list);
        return rspData;
    }

    /**
    * 条件查询所有 系统用户角色关系表
    */
    @GetMapping("/list")
    public PageDataInfo list(SysUserRole sysUserRole) {
        startPage();
        List<SysUserRole> list = sysUserRoleService.selectSysUserRoleList(sysUserRole);
        //PageInfo pageInfo = new PageInfo(list);
        return getDataTable(list);
    }

    /**
     * 删除系统用户角色关系表
     */
    @Log(title = "删除用户和角色",businessType = BusinessType.DELETE)
    @GetMapping("/deleteUserRole")
    public Result removeUserRole(SysUserRole sysUserRole) {
        if(sysUserRoleService.deleteSysUserRole(sysUserRole)>0){
            refreshOrKickUserSession(sysUserRole.getUserId());
            return Result.success();
        }else {
            return Result.error();
        }
    }

    /**
     * 删除系统用户角色关系表
     */
    @Log(title = "批量删除用户和角色",businessType = BusinessType.DELETE)
    @PutMapping("/batchDeleteUserRole")
    public Result removeUserRoleList(@RequestBody List<SysUserRole> sysUserRoleList) {
        if(sysUserRoleService.batchDeleteSysUserRole(sysUserRoleList)>0){
            if (sysUserRoleList != null) {
                for (SysUserRole ur : sysUserRoleList) {
                    if (ur != null) {
                        refreshOrKickUserSession(ur.getUserId());
                    }
                }
            }
            return Result.success();
        }else {
            return Result.error();
        }
    }

    /**
     * 用户角色变更后：刷新该用户 Redis 会话中的角色列表（含 dataScope）；
     * 找不到会话则忽略。数据范围过滤本身已改为读角色缓存，不依赖会话旧值。
     */
    private void refreshOrKickUserSession(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            SysUser target = sysUserService.selectSysUserById(userId);
            if (target == null || StringUtils.isEmpty(target.getUsername())) {
                return;
            }
            LoginUser cached = (LoginUser) sysUserTokenService.getLoginUserByUsername(target.getUsername());
            if (cached == null) {
                return;
            }
            SysUser fresh = sysUserService.getSysUserInfoBy(target.getUsername());
            if (fresh != null) {
                cached.setSysRoleList(cached.build(fresh.getSysRoleList()));
                cached.setSysDept(cached.buildDept(fresh));
                sysUserTokenService.setLoginUser(cached);
            }
        } catch (Exception ignored) {
        }
    }
}
