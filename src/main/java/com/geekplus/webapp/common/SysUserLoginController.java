package com.geekplus.webapp.common;

import com.geekplus.common.annotation.RepeatLogin;
import com.geekplus.common.constant.Constant;
import com.geekplus.common.core.controller.BaseController;
import com.geekplus.common.domain.LoginBody;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.domain.Result;
import com.geekplus.common.enums.ApiExceptionEnum;
import com.geekplus.framework.web.exception.BusinessException;
import com.geekplus.common.util.LogUtil;
import com.geekplus.common.redis.RedisUtil;
import com.geekplus.common.util.regex.RegexUtil;
import com.geekplus.common.util.email.EmailUtil;
import com.geekplus.common.util.http.ServletUtil;
import com.geekplus.common.util.string.RandomName;
import com.geekplus.common.util.string.RandomUtil;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.common.util.http.IPUtils;
import com.geekplus.common.util.encrypt.EncryptUtil;
import com.geekplus.common.util.sysmenu.SysMenuUtil;
import com.geekplus.common.util.uuid.UUIDUtil;
import com.geekplus.framework.jwtshiro.JwtUtil;
import com.geekplus.webapp.system.entity.SysMenu;
import com.geekplus.webapp.system.entity.SysUser;
import com.geekplus.webapp.system.entity.SysUserRole;
import com.geekplus.webapp.system.service.SysMenuService;
import com.geekplus.webapp.system.service.SysUserRoleService;
import com.geekplus.webapp.system.service.SysUserService;
import com.geekplus.webapp.common.service.SysUserTokenService;
import com.geekplus.webapp.system.service.impl.SysConfigServiceImpl;
import com.geekplus.webapp.system.vo.SysRoleVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * author     : geekplus
 * date       : 6/18/23 23:01
 * description:
 */
@Slf4j
@RestController
@RequestMapping("/sys/user")
public class SysUserLoginController extends BaseController {

    @Resource
    private SysUserService sysUserService;
    @Resource
    private SysMenuService sysMenuService;
    @Resource
    private SysUserRoleService sysUserRoleService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private SysUserTokenService tokenService;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private EmailUtil emailUtil;

    @Resource
    private SysConfigServiceImpl configService;

    @Resource
    private com.geekplus.webapp.system.service.cache.RbacCacheService rbacCacheService;

    @PostMapping("/login")
    @RepeatLogin
    public Result login(@RequestBody LoginBody loginBody, HttpServletResponse response){
        boolean captchaOnOff = configService.selectCaptchaOnOff();
        //添加用户认证信息
        //log.info("数据："+validateKey+"验证码："+validateCode);
        //自己系统的密码加密方式 ,这里简单示例一下MD5
        //String md5Password = DigestUtils.md5DigestAsHex(password.getBytes());
        //Object salt=ByteSource.Util.bytes("userId");
        //Object md5Password=new SimpleHash("md5", password, salt, 1024);
        loginBody.setPassword(EncryptUtil.md5EncryptPwd(loginBody.getPassword()));
        //String captcha = redisUtil.getCacheObject(loginBody.getValidateKey());
        //redisUtil.del(loginBody.getValidateKey());
        // 验证码开关
        if (captchaOnOff)
        {
            validateCaptcha(loginBody, loginBody.getValidateCode(), loginBody.getValidateKey());
        }
        SysUser sysUserInfo =sysUserService.getSysUserInfoBy(loginBody.getUsername());
        if(sysUserInfo==null) {
            LogUtil.recordLoginInfo(loginBody, Constant.LOGIN_FAIL,ApiExceptionEnum.LOGIN_USERNAME_ERROR.getMsg());
            throw new BusinessException(ApiExceptionEnum.LOGIN_USERNAME_ERROR);
        }
        if(!Constant.STATUS_NORMAL_I.equals(sysUserInfo.getStatus())) {
            LogUtil.recordLoginInfo(loginBody, Constant.LOGIN_FAIL,ApiExceptionEnum.LOGIN_DISABLED_ERROR.getMsg());
            throw new BusinessException(ApiExceptionEnum.LOGIN_DISABLED_ERROR);
        }else if(!loginBody.getPassword().equals(sysUserInfo.getPassword())){
            LogUtil.recordLoginInfo(loginBody, Constant.LOGIN_FAIL,ApiExceptionEnum.LOGIN_PASSWORD_ERROR.getMsg());
            throw new BusinessException(ApiExceptionEnum.LOGIN_PASSWORD_ERROR);
        }
        // 认证通过后再写登录 IP，避免失败登录也打库
        sysUserService.updateSysUserByUsername(loginBody.getUsername(), IPUtils.getIpAddr(ServletUtil.getRequest()));

        LoginUser loginUser = new LoginUser(sysUserInfo, Collections.emptySet());
        // 角色缓存后台预热（不阻塞返回 token），getMenu 大概率命中
        warmRoleCacheAsync(loginUser.getSysRoleList());

        LogUtil.recordLoginInfo(loginBody, Constant.LOGIN_SUCCESS,"登录成功");
        Map<String, Object> res = new HashMap<>();
        res.put("token", tokenService.createToken(loginUser));
        return Result.success(res);
    }

    private void warmRoleCacheAsync(List<SysRoleVO> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        final List<Long> roleIds = roles.stream()
                .filter(r -> r != null && r.getRoleId() != null)
                .map(SysRoleVO::getRoleId)
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return;
        }
        try {
            com.geekplus.framework.manager.AsyncManager.me().execute(new java.util.TimerTask() {
                @Override
                public void run() {
                    for (Long roleId : roleIds) {
                        rbacCacheService.getRolePerms(roleId);
                        rbacCacheService.getRoleMenus(roleId);
                    }
                }
            });
        } catch (Exception e) {
            log.warn("异步预热角色缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 校验验证码
     *
     * @param loginBody 用户
     * @param code 验证码
     * @param verifyKey redis key标识
     * @return 结果
     */
    public void validateCaptcha(LoginBody loginBody, String code, String verifyKey)
    {
        String captcha = redisUtil.getCacheObject(verifyKey);
        redisUtil.del(verifyKey);
        if (captcha == null)
        {
            //AsyncManager.me().execute(LogFactory.recordLoginInfo(username, Constant.LOGIN_FAIL, ApiExceptionEnum.CODE_ERROR.getMsg()));
            LogUtil.recordLoginInfo(loginBody, Constant.LOGIN_FAIL, ApiExceptionEnum.CODE_IS_NULL.getMsg());
            throw new BusinessException(ApiExceptionEnum.CODE_IS_NULL);
        }
        if (!code.equalsIgnoreCase(captcha))
        {
            //AsyncManager.me().execute(LogFactory.recordLoginInfo(username, Constant.LOGIN_FAIL, ApiExceptionEnum.CODE_ERROR.getMsg()));
            LogUtil.recordLoginInfo(loginBody, Constant.LOGIN_FAIL, ApiExceptionEnum.CODE_ERROR.getMsg());
            throw new BusinessException(ApiExceptionEnum.CODE_ERROR);
        }
    }

    @PostMapping("/register")
    public Result userRegister(@RequestBody LoginBody loginBody){
        if(loginBody.getValidateCode()==redisUtil.get(loginBody.getValidateKey())||loginBody.getValidateCode().equalsIgnoreCase(redisUtil.get(loginBody.getValidateKey()).toString())) {
            // SysUser sysUserInfo = sysUserService.getSysUserInfoBy(loginBody.getUserName());
            // Optional<SysUser> entityOptional = Optional.ofNullable(sysUserInfo);
            // 对象不为空
            // if (entityOptional.isPresent()) {}
            SysUser sysUser = new SysUser();
            sysUser.setUsername(loginBody.getUsername());
            sysUser.setPassword(EncryptUtil.md5EncryptPwd(loginBody.getPassword()));
            sysUser.setNickname(RandomName.randomName(true, 3));
            sysUser.setEmail(loginBody.getEmail());
            if(sysUserService.selectCountByUsername(loginBody.getUsername()) == 0) {
                if(sysUserService.insertSysUserEnCodePwd(sysUser)>0){
                    SysUserRole sysUserRole = new SysUserRole();
                    sysUserRole.setUserId(sysUser.getUserId());
                    sysUserRole.setRoleId(Long.parseLong("2"));
                    sysUserRoleService.deleteSysUserRoleById(sysUser.getUserId());
                    sysUserRoleService.insertSysUserRole(sysUserRole);
                }else {
                    throw new BusinessException("注册失败");
                }
            }else {
                throw new BusinessException("当前用户名已存在");
            }
        }else {
            throw new BusinessException(ApiExceptionEnum.CODE_ERROR);
        }
        return Result.success("注册成功");
    }

    @GetMapping("/getValidateCode")
    public Result senEmailCode(@RequestParam String email){
        if(RegexUtil.isValidEmail(email)) {
            if(sysUserService.selectCountByEmail(email) == 0){
                String code = RandomUtil.generateByRandom(6);
                emailUtil.sendEmail(email, "麦壳市集，拾光无限，本次验证码为：" + code + "，有效期为1分钟", "text", "GeekPlus验证码");
                String redisKey = UUIDUtil.getUUIDTimeStamp();
                redisUtil.set(redisKey, code, 60);
                return Result.success("验证码发送成功", redisKey);
            }else {
                throw new BusinessException("当前邮箱已存在");
            }
        }else {
            return Result.error("发送失败");
        }
    }

    @GetMapping("/getMenu")
    public Result getMenuList(){
        HttpServletRequest request=ServletUtil.getRequest();
        LoginUser loginUser = tokenService.getLoginUser(request);
        String userName = jwtUtil.getUserNameFromToken(tokenService.getToken(request));
        Map<String,Object> map=new HashMap<>();
        // 菜单 / 权限优先走角色级双重缓存；路由由 menus 过滤 B，不另存 routes
        List<SysMenu> routeMenus = rbacCacheService.resolveRouteMenus(loginUser.getSysRoleList());
        if (routeMenus == null || routeMenus.isEmpty()) {
            routeMenus = sysMenuService.getMenuPermsByUsername(userName).stream()
                    .filter(sysMenu -> sysMenu.getMenuType() == null || !sysMenu.getMenuType().equals("B"))
                    .collect(Collectors.toList());
        }
        Set permsSet = rbacCacheService.resolvePerms(loginUser.getSysRoleList());
        if (permsSet == null || permsSet.isEmpty()) {
            permsSet = loginUser.getSysMenuList();
        }
        List<SysMenu> menuList = SysMenuUtil.getParentMenuList(routeMenus);
        Set roleSet=loginUser.getSysRoleList().stream().filter(sysRole -> !StringUtils.isEmpty(sysRole.getRoleKey())).map(SysRoleVO::getRoleKey).collect(Collectors.toSet());
        Set roleNameSet=loginUser.getSysRoleList().stream().filter(sysRole -> !StringUtils.isEmpty(sysRole.getRoleName())).map(SysRoleVO::getRoleName).collect(Collectors.toSet());
        map.put("username", userName);
        map.put("nickname", loginUser.getNickname());
        map.put("userId", loginUser.getUserId());
        map.put("avatar", loginUser.getAvatar());
        map.put("userType", loginUser.getUserType());
        map.put("menuList", menuList);
        map.put("permsSet", permsSet);
        map.put("roles", roleSet);
        map.put("roleNames", roleNameSet);
        map.put("permVer", rbacCacheService.currentPermVer());
        return Result.success(map);
    }

    /* 登出操作 */
    @PostMapping("/logout")
    public Result logout() {
        Subject subject = SecurityUtils.getSubject();
        if (subject != null) {
            subject.logout();
        }
        HttpServletRequest request=ServletUtil.getRequest();
        String token = tokenService.getToken(request);
        tokenService.removeOnlineToken(token);
        //tokenService.delLoginUser(token);
        return Result.success();
    }

    /**
      * @Author geekplus
      * @Description //获取当前用户的权限菜单Menus
      */
    @PostMapping("/getRoutesMenu")
    public Result getRoutesMenu() {
        String userName = tokenService.getSysUserName();
        return Result.success(sysMenuService.getMenuTreeByUsername(userName));
    }

    /**
     * @Author geekplus
     * @Description //获取当前用户的权限菜单Menus
     */
    @GetMapping("/refreshUserAuth")
    public Result refreshUserAuth() {
        String userName = tokenService.getSysUserName();
        SysUser sysUserInfo =sysUserService.getSysUserInfoBy(userName);
        // 会话瘦身：perms 不写入 Redis，由 RBAC 缓存提供；此处仅刷新用户/角色基本信息
        LoginUser loginUser = new LoginUser(sysUserInfo, Collections.emptySet());
        tokenService.refreshTokenUser(loginUser);
        // 确保当前角色缓存可用
        if (loginUser.getSysRoleList() != null) {
            for (SysRoleVO r : loginUser.getSysRoleList()) {
                if (r != null && r.getRoleId() != null) {
                    rbacCacheService.getRolePerms(r.getRoleId());
                }
            }
        }
        return Result.success("权限已刷新");
    }

    /** 管理端手动刷新全部 RBAC 缓存 */
    @GetMapping("/refreshRbacCache")
    public Result refreshRbacCache() {
        rbacCacheService.evictAllRoles();
        rbacCacheService.warmUp();
        return Result.success("RBAC 缓存已刷新");
    }

    /**
     * 未登录
     */
    @RequestMapping(value = "/unLogin", method = RequestMethod.GET)
    public void notLogin() {
        throw new BusinessException(ApiExceptionEnum.LOGIN_AUTH);
    }

    /**
     * 无权限访问
     */
    @RequestMapping(value = "/unAuth", method = RequestMethod.GET)
    public void notRole() {
        throw new BusinessException(ApiExceptionEnum.PERMISSION);
    }
}
