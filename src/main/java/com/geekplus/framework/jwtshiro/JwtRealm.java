package com.geekplus.framework.jwtshiro;

import com.geekplus.common.core.LoggerFactory;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.webapp.common.service.SysUserTokenService;
import com.geekplus.webapp.system.service.cache.RbacCacheService;
import com.geekplus.webapp.system.vo.SysRoleVO;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统用户认证授权：权限从 RBAC 角色缓存组装，会话不再携带大权限集。
 */
public class JwtRealm extends AuthorizingRealm {
    private static Logger log = LoggerFactory.getLogger(JwtRealm.class.getName());

    @Autowired
    private SysUserTokenService tokenService;

    @Autowired
    private RbacCacheService rbacCacheService;

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principalCollection) {
        LoginUser loginUser = (LoginUser) principalCollection.getPrimaryPrincipal();
        List<SysRoleVO> roles = loginUser.getSysRoleList();
        if (roles == null) {
            roles = Collections.emptyList();
        }
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        Set roleSet = roles.stream()
                .filter(sysRole -> !StringUtils.isEmpty(sysRole.getRoleKey()))
                .map(SysRoleVO::getRoleKey)
                .collect(Collectors.toSet());
        Set<String> permSet = rbacCacheService.resolvePerms(roles);
        // 兼容旧会话仍带有权限集的情况
        if ((permSet == null || permSet.isEmpty()) && loginUser.getSysMenuList() != null) {
            permSet = loginUser.getSysMenuList();
        }
        info.addRoles(roleSet);
        info.addStringPermissions(permSet);
        if (log.isDebugEnabled()) {
            log.debug("授权用户={} roles={} perms={}", loginUser.getUsername(), roleSet.size(),
                    permSet == null ? 0 : permSet.size());
        }
        return info;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken)
            throws AuthenticationException {
        String token = (String) authenticationToken.getCredentials();
        if (token == null) {
            log.warn("身份认证失败：token 为空");
            throw new AuthenticationException("认证缺失！非法无效!");
        }
        LoginUser loginUser = tokenService.checkUserTokenGetLoginUser(token);
        if (log.isDebugEnabled()) {
            log.debug("认证通过用户={}", loginUser.getUsername());
        }
        return new SimpleAuthenticationInfo(loginUser, token, getName());
    }

    @Override
    public void clearCache(PrincipalCollection principals) {
        super.clearCache(principals);
    }
}
