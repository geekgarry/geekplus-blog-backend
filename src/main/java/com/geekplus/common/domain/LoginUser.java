package com.geekplus.common.domain;

import com.geekplus.webapp.system.entity.SysDept;
import com.geekplus.webapp.system.entity.SysRole;
import com.geekplus.webapp.system.entity.SysUser;
import com.geekplus.webapp.system.vo.SysDeptVO;
import com.geekplus.webapp.system.vo.SysRoleVO;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * author     : geekplus
 * description: 登录用户信息，负责登录成功后设置相关用户的角色和权限
 */
public class LoginUser implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 登录IP
     */
    private String loginIp;

    /**
     * 登录时间
     */
    private Date loginTime;

    /**
     * 登录地址
     */
    private String loginLocation;

    private String tokenId;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    //用户信息，用户角色和用户权限菜单
    //private SysUser sysUser;

    //用户的基本信息
    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String phoneNumber;
    private String avatar;
    private Integer gender;
    /** 用户类型：0普通 1系统管理员 2网站管理员 3开发者 … */
    private Integer userType;

    //用户所属部门
    private SysDeptVO sysDept;
    /** 部门 ID 兜底（会话瘦身后仍可供「本部门」数据权限使用） */
    private Long deptId;

    //用户角色
    private List<SysRoleVO> sysRoleList;

    //权限信息
    private Set<String> sysMenuList;

    public LoginUser()
    {}

    public LoginUser(Set<String> permissionsMenu)
    {
        this.sysMenuList = permissionsMenu;
    }

    public LoginUser(SysUser sysUser, Set<String> permissionsMenu)
    {
        this.userId = sysUser.getUserId();
        this.username = sysUser.getUsername();
        this.nickname = sysUser.getNickname();
        this.email = sysUser.getEmail();
        this.phoneNumber = sysUser.getPhoneNumber();
        this.avatar = sysUser.getAvatar();
        this.gender = sysUser.getGender();
        this.userType = sysUser.getUserType();
        if (sysUser.getDeptId() != null) {
            this.deptId = sysUser.getDeptId().longValue();
        }
        setSysDept(buildDept(sysUser));
        setSysRoleList(build(sysUser.getSysRoleList()));
        this.sysMenuList = permissionsMenu;
    }

    /** 刷新权限时保留原会话元数据（tokenId / UA / IP 等），避免丢在线态。 */
    public void copySessionMetaFrom(LoginUser other)
    {
        if (other == null)
        {
            return;
        }
        this.tokenId = other.tokenId;
        this.loginIp = other.loginIp;
        this.loginLocation = other.loginLocation;
        this.browser = other.browser;
        this.os = other.os;
        this.loginTime = other.loginTime;
    }

    /**
     * 构建部门 VO；无关联对象时用用户上的 deptId 兜底，供数据权限「本部门」使用。
     */
    public SysDeptVO buildDept(SysUser sysUser) {
        if (sysUser == null) {
            return null;
        }
        SysDept sysDept = sysUser.getSysDept();
        if (sysDept != null && sysDept.getDeptId() != null) {
            SysDeptVO vo = new SysDeptVO();
            vo.setDeptId(sysDept.getDeptId());
            vo.setDeptName(sysDept.getDeptName());
            vo.setLeader(sysDept.getLeader());
            return vo;
        }
        if (sysUser.getDeptId() != null) {
            SysDeptVO vo = new SysDeptVO();
            vo.setDeptId(sysUser.getDeptId().longValue());
            return vo;
        }
        return null;
    }

    /**
      * @Description //构建新的SysDept显示对象
      * @deprecated 请用 {@link #buildDept(SysUser)}，避免 sysDept 为空 NPE
      */
    @Deprecated
    public SysDeptVO build(SysDept sysDept) {
        if (sysDept == null) {
            return null;
        }
        SysDeptVO sysDeptVO = new SysDeptVO();
        sysDeptVO.setDeptId(sysDept.getDeptId());
        sysDeptVO.setDeptName(sysDept.getDeptName());
        sysDeptVO.setLeader(sysDept.getLeader());
        return sysDeptVO;
    }

    /**
     * @Description //构建新的SysRole显示对象
     */
    public List<SysRoleVO> build(List<SysRole> sysRoleList) {
        if (sysRoleList == null || sysRoleList.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return sysRoleList.stream().map(role -> {
            SysRoleVO sysRoleVO = new SysRoleVO();
            sysRoleVO.setRoleId(role.getRoleId());
            sysRoleVO.setRoleName(role.getRoleName());
            sysRoleVO.setRoleKey(role.getRoleKey());
            // 登录会话需保留 dataScope，供列表等查询的数据权限切面使用
            sysRoleVO.setDataScope(role.getDataScope());
            return sysRoleVO;
        }).collect(Collectors.toList());
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public Integer getUserType() {
        return userType;
    }

    public void setUserType(Integer userType) {
        this.userType = userType;
    }

    public SysDeptVO getSysDept() {
        return sysDept;
    }

    public void setSysDept(SysDeptVO sysDept) {
        this.sysDept = sysDept;
        if (sysDept != null && sysDept.getDeptId() != null) {
            this.deptId = sysDept.getDeptId();
        }
    }

    public Long getDeptId() {
        if (deptId != null) {
            return deptId;
        }
        if (sysDept != null) {
            return sysDept.getDeptId();
        }
        return null;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public List<SysRoleVO> getSysRoleList() {
        return sysRoleList;
    }

    public void setSysRoleList(List<SysRoleVO> sysRoleList) {
        this.sysRoleList = sysRoleList;
    }

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public String getLoginLocation() {
        return loginLocation;
    }

    public void setLoginLocation(String loginLocation) {
        this.loginLocation = loginLocation;
    }

    public Date getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Date loginTime) {
        this.loginTime = loginTime;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public Set<String> getSysMenuList() {
        return sysMenuList;
    }

    public void setSysMenuList(Set<String> sysMenuList) {
        this.sysMenuList = sysMenuList;
    }
}
