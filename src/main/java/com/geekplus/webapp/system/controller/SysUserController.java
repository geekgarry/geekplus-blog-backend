package com.geekplus.webapp.system.controller;

import cn.hutool.core.bean.BeanUtil;
import com.geekplus.common.annotation.DataScope;
import com.geekplus.common.annotation.Log;
import com.geekplus.common.annotation.RepeatSubmit;
import com.geekplus.common.config.WebAppConfig;
import com.geekplus.common.constant.Constant;
import com.geekplus.common.constant.HttpStatus;
import com.geekplus.common.core.controller.BaseController;
import com.geekplus.common.datascope.DataPermissionHelper;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.domain.Result;
import com.geekplus.common.enums.BusinessType;
import com.geekplus.common.enums.OperatorType;
import com.geekplus.common.page.PageDataInfo;
import com.geekplus.common.util.datetime.DateUtil;
import com.geekplus.common.util.encrypt.SignatureUtil;
import com.geekplus.common.util.http.ServletUtil;
import com.geekplus.common.util.file.FileUploadUtils;
import com.geekplus.common.util.file.FileUtils;
import com.geekplus.common.util.poi.ExcelUtil;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.framework.jwtshiro.JwtUtil;
import com.geekplus.webapp.system.entity.SysDept;
import com.geekplus.webapp.system.entity.SysRole;
import com.geekplus.webapp.system.entity.SysUser;
import com.geekplus.webapp.common.service.SysUserTokenService;
import com.geekplus.webapp.system.service.SysRoleService;
import com.geekplus.webapp.system.service.SysUserRoleService;
import com.geekplus.webapp.system.service.SysUserService;
import com.geekplus.webapp.system.vo.SysDeptVO;
import com.github.pagehelper.PageInfo;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统用户表 系统用户表
 * Created by CodeGenerator on 2023/06/18.
 */
@RestController
@RequestMapping("/sys/user")
public class SysUserController extends BaseController {
    @Resource
    private SysUserService sysUserService;
    @Resource
    private SysRoleService sysRoleService;
    @Resource
    private DataPermissionHelper dataPermissionHelper;
    @Resource
    private SysUserRoleService sysUserRoleService;
    @Resource
    private SysUserTokenService sysUserTokenService;
    @Resource
    private JwtUtil jwtUtil;
    @Autowired
    private SignatureUtil signer;
    @Autowired
    private WebAppConfig appConfig;

    /**
     * 增加 系统用户表
     */
    @RequiresPermissions("system:user:add")
    @Log(title = "添加系统用户",businessType = BusinessType.INSERT,operatorType = OperatorType.MANAGE)
    @PostMapping("/add")
    @RepeatSubmit
    public Result add(@RequestBody SysUser sysUser) {
        // 所选部门须在当前登录者数据权限可见范围内
        Result denyDept = assertCanAssignDept(sysUser == null ? null : sysUser.getDeptId());
        if (denyDept != null) {
            return denyDept;
        }
        return toResult(sysUserService.insertSysUser(sysUser));
    }

    @RequiresPermissions("system:user:add")
    @Log(title = "系统添加用户信息",businessType = BusinessType.INSERT,operatorType = OperatorType.MANAGE)
    @PostMapping("/addEncodePwd")
    @RepeatSubmit
    public Result addEncodePwd(@RequestBody SysUser sysUser) {
        Result denyDept = assertCanAssignDept(sysUser == null ? null : sysUser.getDeptId());
        if (denyDept != null) {
            return denyDept;
        }
        Result result=toResult(sysUserService.insertSysUserEnCodePwd(sysUser));
        result.put("userId",sysUser.getUserId());
        return result;
    }

    /**
    * 删除 系统用户表
    */
    @Transactional(rollbackFor = Exception.class)
    @RequiresPermissions("system:user:delete")
    @Log(title = "删除用户",businessType = BusinessType.DELETE,operatorType = OperatorType.MANAGE)
    @GetMapping("/delete")
    public Result remove(@RequestParam Long userId) {
        Result deny = assertCanAccessUser(userId);
        if (deny != null) {
            return deny;
        }
        if(sysUserService.deleteSysUserById(userId)>0){
            return toResult(sysUserRoleService.deleteSysUserRoleById(userId));
        }else {
            return Result.error("删除失败");
        }
    }

    /**
     * 逻辑删除 系统用户表
     */
    @RequiresPermissions("system:user:delete")
    @Log(title = "逻辑删除用户",businessType = BusinessType.DELETE,operatorType = OperatorType.MANAGE)
    @GetMapping("/logical_delete")
    public Result modifyDelFlagById(@RequestParam Long userId) {
        Result deny = assertCanAccessUser(userId);
        if (deny != null) {
            return deny;
        }
        return toResult(sysUserService.updateDelFlagById(userId));
    }

    /**
    * 批量删除 系统用户表
    */
    @Transactional(rollbackFor = Exception.class)
    @RequiresPermissions("system:user:delete")
    @Log(title = "批量删除用户",businessType = BusinessType.DELETE,operatorType = OperatorType.MANAGE)
    @DeleteMapping("/{userIds}")
    public Result remove(@PathVariable Long[] userIds) {
        if (userIds != null) {
            for (Long id : userIds) {
                Result deny = assertCanAccessUser(id);
                if (deny != null) {
                    return deny;
                }
            }
        }
        if(sysUserService.deleteSysUserByIds(userIds)>0){
            return toResult(sysUserRoleService.deleteSysUserRoleByIds(userIds));
        }else {
            return Result.error("删除失败");
        }
    }

    @RequiresPermissions("system:user:delete")
    @Log(title = "批量逻辑删除用户",businessType = BusinessType.DELETE,operatorType = OperatorType.MANAGE)
    @DeleteMapping("/logical/{userIds}")
    public Result modifyDelFlagByIds(@PathVariable Long[] userIds) {
        if (userIds != null) {
            for (Long id : userIds) {
                Result deny = assertCanAccessUser(id);
                if (deny != null) {
                    return deny;
                }
            }
        }
        return toResult(sysUserService.updateDelFlagByIds(userIds));
    }

    /**
    * 更新 系统用户表
    */
    @RequiresPermissions("system:user:update")
    @Log(title = "更新用户信息",businessType = BusinessType.UPDATE,operatorType = OperatorType.MANAGE)
    @PutMapping
    public Result edit(@RequestBody SysUser sysUser) {
        if (sysUser.getUserId() != null) {
            Result deny = assertCanAccessUser(sysUser.getUserId());
            if (deny != null) {
                return deny;
            }
        }
        // 改部门时同样校验目标部门是否可见
        Result denyDept = assertCanAssignDept(sysUser.getDeptId());
        if (denyDept != null) {
            return denyDept;
        }
        return toResult(sysUserService.updateSysUser(sysUser));
    }

    /**
     * 更新当前登录用户个人资料（支持用户名、备注）；成功后强制下线需重新登录
     */
    @Log(title = "更新个人资料", businessType = BusinessType.UPDATE, operatorType = OperatorType.MANAGE)
    @PutMapping("/updateUserProfile")
    public Result updateUserProfile(@RequestBody SysUser sysUser) {
        LoginUser loginUser = sysUserTokenService.getLoginUser(ServletUtil.getRequest());
        if (loginUser == null) {
            return Result.error("未登录");
        }
        String oldUsername = loginUser.getUsername();
        String newUsername = sysUser.getUsername();
        if (StringUtils.isEmpty(newUsername)) {
            newUsername = oldUsername;
            sysUser.setUsername(oldUsername);
        }
        // 只能改自己
        sysUser.setUserId(loginUser.getUserId());
        // 用户名变更时校验唯一
        if (!oldUsername.equals(newUsername)) {
            if (sysUserService.selectCountByUsername(newUsername) > 0) {
                return Result.error("用户名已存在");
            }
        }
        if (sysUserService.updateSysUser(sysUser) > 0) {
            // 个人资料变更后强制重新登录（用户名变更时旧 Redis key 必须清理）
            sysUserTokenService.invalidateUserSessions(oldUsername);
            if (!oldUsername.equals(newUsername)) {
                sysUserTokenService.invalidateUserSessions(newUsername);
            }
            Result result = Result.success("修改成功，请重新登录");
            result.put("forceRelogin", true);
            return result;
        }
        return Result.error();
    }

    /**
     * 管理员重置系统用户密码
     */
    @RequiresPermissions("system:user:resetPwd")
    @Log(title = "重置用户密码",businessType = BusinessType.UPDATE,operatorType = OperatorType.MANAGE)
    @GetMapping("/resetUserPwd")
    public Result resetUserPwd(SysUser sysUser) {
        if (sysUser != null && sysUser.getUserId() != null) {
            Result deny = assertCanAccessUser(sysUser.getUserId());
            if (deny != null) {
                return deny;
            }
        }
        return toResult(sysUserService.updateSysUserPwd(sysUser));
    }

    /**
     * 用户修改系统用户密码
     */
    @RequiresPermissions("system:user:update")
    @Log(title = "修改用户密码",businessType = BusinessType.UPDATE,operatorType = OperatorType.MANAGE)
    @GetMapping("/updateUserPwd")
    @RepeatSubmit
    public Result updateUserPwd(String oldPassword, String newPassword) {
        //Session session= SecurityUtils.getSubject().getSession();
        //Long userId=Long.parseLong(session.getAttribute("userId").toString());
        SysUser sysUser=new SysUser();
        sysUser.setUserId(sysUserTokenService.getSysUserId(ServletUtil.getRequest()));
        sysUser.setPassword(oldPassword);
        if(sysUserService.selectSysUserByPassword(sysUser)!=null){
            sysUser.setPassword(newPassword);
            return toResult(sysUserService.updateSysUserPwd(sysUser));
        }else{
            return Result.error("原密码不正确");
        }
    }

    /**
    * 单条数据详情 系统用户表
    */
    @RequiresPermissions("system:user:detail")
    @GetMapping("/detail")
    public Result detail(@RequestParam Long userId) {
        Result deny = assertCanAccessUser(userId);
        if (deny != null) {
            return deny;
        }
        SysUser sysUser = sysUserService.selectSysUserById(userId);
        List<SysRole> sysRoles=sysRoleService.getRolesByUserId(userId.toString());
        sysUser.setSysRoleList(sysRoles);
        return Result.success(sysUser);
    }

    /**
     * 系统用户信息
     */
    @GetMapping("/userProfile")
    public Result userProfile() {
        //LoginUser loginUser = new LoginUser();
        SysUser sysUser = null;
        String token = sysUserTokenService.getToken(ServletUtil.getRequest());
        if(StringUtils.isNotEmpty(token)) {
            //if (jwtUtil.verify(token)) {
                //sysUser = sysUserTokenService.getLoginUser(ServletUtil.getRequest());
            //}
            String userName= jwtUtil.getUserNameFromToken(token);
            sysUser = sysUserService.getSysUserInfoBy(userName);
            //sysUser.setAvatar(signer.signedUrl(sysUser.getAvatar()));
            //SysUser sysUser = sysUserService.selectSysUserById(userId);

        }
        return Result.success(sysUser);
    }

    /**
     * 更新当前用户头像 系统用户表
     */
    @GetMapping("/updateAvatar")
    public Result updateAvatar(String avatar) {
        String userName= sysUserTokenService.getSysUserName();
        boolean isUpdate=sysUserService.updateUserAvatar(userName,avatar);
        LoginUser loginUser = sysUserTokenService.getLoginUser(ServletUtil.getRequest());
        loginUser.setAvatar(avatar);
        sysUserTokenService.setLoginUser(loginUser);
        return isUpdate?Result.success((Object)avatar):Result.error();
    }

    /**
     * 头像上传
     */
    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
    @PostMapping("/avatar")
    public Result avatar(@RequestParam("avatarFile") MultipartFile file, @RequestParam(required = false) String fileFolder) throws IOException
    {
        if (!file.isEmpty())
        {
            LoginUser loginUser = sysUserTokenService.getLoginUser(ServletUtil.getRequest());
            String avatar = null;
            if(StringUtils.isEmpty(fileFolder)) {
                avatar = FileUploadUtils.upload(appConfig.getAvatarPath(), file);
            }else {
                avatar = FileUploadUtils.upload(appConfig.getAvatarPath() + File.separator + fileFolder, file);
            }

            if (sysUserService.updateUserAvatar(loginUser.getUsername(), avatar))
            {
                Result ajax = Result.success();
                ajax.put("imgUrl", avatar);
                // 更新缓存用户头像
                loginUser.setAvatar(avatar);
                sysUserTokenService.setLoginUser(loginUser);
                return ajax;
            }
        }
        return Result.error("上传图片异常，请联系管理员");
    }

    /**
     * 在线浏览所有用户头像
     */
    @GetMapping("/getAvatarList")
    public Result getAvatarList(@RequestParam(required = false) String fileFolder)
    {
        File file=new File(appConfig.getUploadPath()+ File.separator + "avatar" + File.separator + fileFolder);
        List<String> list= new ArrayList<>();
        FileUtils.getDirectoryAllFile(file,list);
        return Result.success(list);
    }

    /**
    * 条件查询所有 系统用户表（{@code @DataScope} 挂在 Controller，对齐系统，保证切面必织入）
    */
    @RequiresPermissions("system:user:listAll")
    @DataScope
    @GetMapping("/listAll")
    public PageDataInfo listAll(SysUser sysUser) {
        List<SysUser> list = sysUserService.selectSysUserList(sysUser);
        PageDataInfo rspData = new PageDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setMsg("查询成功");
        rspData.setRows(list);
        rspData.setTotal(new PageInfo(list).getTotal());
        return rspData;
    }

    /**
    * 条件查询所有 系统用户表
    */
    @RequiresPermissions("system:user:list")
    @DataScope
    @GetMapping("/list")
    public PageDataInfo list(SysUser sysUser) {
        startPage();
        List<SysUser> list = sysUserService.selectSysUserList(sysUser);
        //PageInfo pageInfo = new PageInfo(list);
//        PageDataInfo rspData = new PageDataInfo();
//        rspData.setCode(HttpStatus.SUCCESS);
//        rspData.setMsg("查询成功");
//        rspData.setRows(list);
//        rspData.setTotal(new PageInfo(list).getTotal());
        //直接调用公共方法
        return getDataTable(list);
    }

    /**
     * 导出系统用户表
     */
    @RequiresPermissions("system:user:export")
    @Log(title = "导出系统用户表", businessType = BusinessType.EXPORT)
    @DataScope
    @GetMapping("/export")
    public Result export(SysUser sysUser)
    {
        List<SysUser> list = sysUserService.selectSysUserList(sysUser);
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        return util.exportExcel(list, "sysUser");
    }

    /** 写操作前校验目标用户是否在当前登录者的数据权限范围内 */
    private Result assertCanAccessUser(Long userId)
    {
        if (userId == null)
        {
            return null;
        }
        LoginUser loginUser = getLoginUser();
        if (loginUser == null)
        {
            return Result.error("未登录");
        }
        SysUser target = sysUserService.selectSysUserById(userId);
        if (target == null)
        {
            return Result.error("用户不存在");
        }
        Long deptId = target.getDeptId() == null ? null : target.getDeptId().longValue();
        if (!dataPermissionHelper.canAccessUser(loginUser, target.getUserId(), deptId))
        {
            return Result.error("没有权限操作该用户数据");
        }
        return null;
    }

    /**
     * 新增/改部门：deptId 须落在操作者可见部门内（管理员不限制）。
     * deptId 为空时不拦（兼容未选部门的历史数据）。
     */
    private Result assertCanAssignDept(Integer deptId)
    {
        if (deptId == null)
        {
            return null;
        }
        LoginUser loginUser = getLoginUser();
        if (loginUser == null)
        {
            return Result.error("未登录");
        }
        if (!dataPermissionHelper.canAccessDept(loginUser, deptId.longValue()))
        {
            return Result.error("没有权限将用户分配到该部门");
        }
        return null;
    }
}
