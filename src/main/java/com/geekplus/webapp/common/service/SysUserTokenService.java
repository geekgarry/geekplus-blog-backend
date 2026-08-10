package com.geekplus.webapp.common.service;

import com.geekplus.common.constant.Constant;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.redis.RedisUtil;
import com.geekplus.common.util.encrypt.SignatureUtil;
import com.geekplus.common.util.http.IPUtils;
import com.geekplus.common.util.http.IpAddressUtil;
import com.geekplus.common.util.http.ServletUtil;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.common.util.uuid.UUIDUtil;
import com.geekplus.framework.jwtshiro.JwtUtil;
import com.geekplus.webapp.system.entity.SysUser;
import com.geekplus.webapp.system.service.SysRoleService;
import com.geekplus.webapp.system.service.SysUserService;
import eu.bitwalker.useragentutils.UserAgent;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Token / Redis 会话：支持 SSO（PC/移动各一）与长期滑动续期。
 */
@Component
@Slf4j
public class SysUserTokenService {

    /** SSO 关闭时防滥用的软上限 */
    private static final int MAX_DEVICES_WHEN_SSO_OFF = 20;

    @Value("${token.header}")
    private String header;

    @Value("${token.expireTime}")
    private Long expireTime;

    /** 默认是否开启单点（可被 sys.account.ssoEnabled 覆盖） */
    @Value("${token.ssoEnabled:true}")
    private boolean ssoEnabledDefault;

    @Resource
    private SysUserService sysUserService;

    @Resource
    private SysRoleService sysRoleService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private JwtUtil jwtUtil;

    @Autowired
    private SignatureUtil signer;

    @Autowired(required = false)
    private com.geekplus.webapp.system.service.impl.SysConfigServiceImpl configService;

    protected final ConcurrentHashMap<String, String> tokenMap = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<String, String> loginUserMap = new ConcurrentHashMap<>();

    public String createToken(LoginUser loginUser) {
        String tokenUuid = UUIDUtil.getShaUUID(loginUser.getUsername());
        // 登录主路径不做外网 IP 归属查询（可耗时数秒）；UA/IP 同步写入，归属地异步回填
        String token = refreshToken(tokenUuid, loginUser, false);
        String clientType = resolveClientType(ServletUtil.getRequest());
        addOnlineToken(loginUser.getUsername(), tokenUuid, clientType);
        enrichLoginLocationAsync(loginUser.getUsername(), loginUser.getLoginIp());
        return token;
    }

    /** 异步回填登录归属地，不阻塞发 token */
    private void enrichLoginLocationAsync(String username, String ip) {
        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(ip)) {
            return;
        }
        final String userKey = username;
        final String loginIp = ip;
        try {
            com.geekplus.framework.manager.AsyncManager.me().execute(new java.util.TimerTask() {
                @Override
                public void run() {
                    try {
                        String loc = IpAddressUtil.getRealAddressByIP(loginIp);
                        LoginUser cached = (LoginUser) redisUtil.get(getTokenKey(userKey));
                        if (cached != null) {
                            cached.setLoginLocation(loc);
                            refreshRedisUser(cached);
                        }
                    } catch (Exception e) {
                        log.warn("异步回填登录归属地失败 user={} err={}", userKey, e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            log.warn("调度归属地任务失败: {}", e.getMessage());
        }
    }

    public String refreshToken(String tokenId, LoginUser loginUser) {
        return refreshToken(tokenId, loginUser, false);
    }

    public String refreshToken(String tokenId, LoginUser loginUser, boolean resolveLocation) {
        loginUser.setLoginTime(new Date());
        String token = jwtUtil.sign(tokenId, loginUser);
        refreshTokenUser(loginUser, resolveLocation);
        return token;
    }

    public void refreshTokenUser(LoginUser loginUser) {
        refreshTokenUser(loginUser, false);
    }

    public void refreshTokenUser(LoginUser loginUser, boolean resolveLocation) {
        setUserAgent(loginUser, resolveLocation);
        setLoginUser(loginUser);
    }

    public LoginUser checkUserTokenGetLoginUser(String token) {
        if (!jwtUtil.verify(token)) {
            throw new AuthenticationException("用户认证失败！token已经过期失效，请重新登录！");
        }
        String username = jwtUtil.getUserNameFromToken(token);
        if (username == null) {
            throw new AuthenticationException("无效登录!");
        }
        String tokenId = jwtUtil.getTokenIdFromToken(token);
        if (!isTokenOnline(username, tokenId)) {
            throw new AuthenticationException("登录凭证已失效！");
        }
        LoginUser user = (LoginUser) redisUtil.get(getTokenKey(username));
        if (user == null) {
            throw new AuthenticationException("非法登录！");
        }
        if (!jwtTokenRefresh(token, user)) {
            throw new AuthenticationException("用户认证过期失效，请重新登录！");
        }
        return user;
    }

    public boolean jwtTokenRefresh(String token, LoginUser loginUser) {
        long currentTimeMillis = System.currentTimeMillis();
        String userKey = jwtUtil.getUserNameFromToken(token);
        if (redisUtil.hasKey(getTokenKey(userKey))) {
            if (jwtUtil.checkRefresh(token, currentTimeMillis)) {
                String newAuthorization = refreshToken(jwtUtil.getTokenIdFromToken(token), loginUser, false);
                log.info("刷新的token: {}", newAuthorization);
                HttpServletResponse response = ServletUtil.getResponse();
                Cookie cookie = new Cookie(header, newAuthorization);
                cookie.setPath("/");
                response.addCookie(cookie);
            }
            return true;
        }
        return false;
    }

    public void setLoginUser(LoginUser loginUser) {
        if (StringUtils.isNotNull(loginUser) && StringUtils.isNotEmpty(loginUser.getUsername())) {
            refreshRedisUser(loginUser);
        }
    }

    public LoginUser getLoginUser(HttpServletRequest request) {
        String token = getToken(request);
        if (StringUtils.isNotEmpty(token)) {
            String userKey = getTokenKey(jwtUtil.getUserNameFromToken(token));
            return (LoginUser) redisUtil.get(userKey);
        }
        return null;
    }

    public void refreshRedisUser(LoginUser loginUser) {
        String userKey = loginUser.getUsername();
        // 会话瘦身：不把大权限集写入 Redis，鉴权时按角色从 RBAC 缓存组装
        loginUser.setSysMenuList(null);
        redisUtil.set(getTokenKey(userKey), loginUser, expireTime, TimeUnit.SECONDS);
        redisUtil.expire(getUserOnlineToken(userKey, "pc"), expireTime, TimeUnit.SECONDS);
        redisUtil.expire(getUserOnlineToken(userKey, "mobile"), expireTime, TimeUnit.SECONDS);
        redisUtil.expire(getUserOnlineToken(userKey, "all"), expireTime, TimeUnit.SECONDS);
    }

    public Long getSysUserId(HttpServletRequest request) {
        Long userId = getLoginUser(request).getUserId();
        if (userId == null && StringUtils.isNotEmpty(getToken(request))) {
            String userName = jwtUtil.getUserNameFromToken(getToken(request));
            SysUser sysUser = sysUserService.sysUserLoginBy(userName);
            return sysUser.getUserId();
        }
        return userId;
    }

    public String getSysUserName() {
        String token = getToken(ServletUtil.getRequest());
        if (StringUtils.isNotEmpty(token)) {
            return jwtUtil.getUserNameFromToken(token);
        }
        return null;
    }

    public void delLoginUser(String token) {
        if (StringUtils.isNotEmpty(token) && jwtUtil.verify(token)) {
            String username = jwtUtil.getUserNameFromToken(token);
            invalidateUserSessions(username);
        }
    }

    /**
     * 清除某用户全部在线会话（改用户名 / 强制下线时用）
     */
    public void invalidateUserSessions(String username) {
        if (StringUtils.isEmpty(username)) {
            return;
        }
        redisUtil.del(getTokenKey(username));
        redisUtil.del(getUserOnlineToken(username));
        redisUtil.del(getUserOnlineToken(username, "pc"));
        redisUtil.del(getUserOnlineToken(username, "mobile"));
        redisUtil.del(getUserOnlineToken(username, "all"));
    }

    private boolean isSsoEnabled() {
        if (configService != null) {
            try {
                return configService.selectSsoOnOff();
            } catch (Exception e) {
                log.debug("读取 SSO 配置失败，使用默认值: {}", ssoEnabledDefault);
            }
        }
        return ssoEnabledDefault;
    }

    public static String resolveClientType(HttpServletRequest request) {
        if (request == null) {
            return "pc";
        }
        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.isEmpty()) {
            return "pc";
        }
        String u = ua.toLowerCase();
        if (u.contains("mobile") || u.contains("android") || u.contains("iphone")
                || u.contains("ipod") || u.contains("ipad") || u.contains("harmonyos")
                || u.contains("micromessenger")) {
            return "mobile";
        }
        return "pc";
    }

    private boolean isTokenOnline(String username, String tokenId) {
        if (StringUtils.isEmpty(tokenId)) {
            return false;
        }
        return listContains(getUserOnlineToken(username), tokenId)
                || listContains(getUserOnlineToken(username, "pc"), tokenId)
                || listContains(getUserOnlineToken(username, "mobile"), tokenId)
                || listContains(getUserOnlineToken(username, "all"), tokenId);
    }

    private boolean listContains(String key, String tokenId) {
        try {
            java.util.List<?> list = redisUtil.lsGet(key, 0, -1);
            return list != null && list.contains(tokenId);
        } catch (Exception e) {
            return false;
        }
    }

    public void addOnlineToken(String username, String tokenId) {
        addOnlineToken(username, tokenId, resolveClientType(ServletUtil.getRequest()));
    }

    public void addOnlineToken(String username, String tokenId, String clientType) {
        if (StringUtils.isEmpty(clientType)) {
            clientType = "pc";
        }
        boolean sso = isSsoEnabled();
        String listKey = sso ? getUserOnlineToken(username, clientType) : getUserOnlineToken(username, "all");
        int limit = sso ? 1 : MAX_DEVICES_WHEN_SSO_OFF;

        redisUtil.lsPush(listKey, tokenId);
        redisUtil.expire(listKey, expireTime, TimeUnit.SECONDS);

        while (redisUtil.lsGetListSize(listKey) > limit) {
            String kickedJti = (String) redisUtil.lsPop(listKey);
            if (kickedJti != null) {
                log.warn("用户 [{}] {} 端在线超限 (SSO={})，踢出 JTI: {}", username, clientType, sso, kickedJti);
            }
        }
        String legacy = getUserOnlineToken(username);
        if (redisUtil.hasKey(legacy) && !legacy.equals(listKey)) {
            redisUtil.del(legacy);
        }
        log.info("用户 [{}] {} 登录成功，JTI: {}，SSO={}，当前该端在线数: {}",
                username, clientType, tokenId, sso, redisUtil.lsGetListSize(listKey));
    }

    public void removeOnlineToken(String token) {
        if (StringUtils.isNotEmpty(token) && jwtUtil.verify(token)) {
            removeOnlineToken(jwtUtil.getUserNameFromToken(token), jwtUtil.getTokenIdFromToken(token));
        }
    }

    public void removeOnlineToken(String userKey, String tokenId) {
        removeFromList(getUserOnlineToken(userKey, "pc"), tokenId);
        removeFromList(getUserOnlineToken(userKey, "mobile"), tokenId);
        removeFromList(getUserOnlineToken(userKey, "all"), tokenId);
        removeFromList(getUserOnlineToken(userKey), tokenId);
        if (!listHasAny(getUserOnlineToken(userKey, "pc"))
                && !listHasAny(getUserOnlineToken(userKey, "mobile"))
                && !listHasAny(getUserOnlineToken(userKey, "all"))
                && !listHasAny(getUserOnlineToken(userKey))) {
            redisUtil.del(getTokenKey(userKey));
        }
    }

    private void removeFromList(String key, String tokenId) {
        try {
            if (redisUtil.hasKey(key)) {
                redisUtil.lsRemove(key, 0, tokenId);
                if (redisUtil.lsGetListSize(key) <= 0) {
                    redisUtil.del(key);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean listHasAny(String key) {
        try {
            return redisUtil.hasKey(key) && redisUtil.lsGetListSize(key) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public String getToken(HttpServletRequest request) {
        String token = request.getHeader(header);
        if (StringUtils.isNotEmpty(token) && token.startsWith(Constant.AUTHENTICATION_PREFIX)) {
            token = token.replace(Constant.AUTHENTICATION_PREFIX, "");
        }
        return token;
    }

    private String getTokenKey(String userKey) {
        return Constant.PRE_REDIS_USER_TOKEN + userKey;
    }

    private String getUserOnlineToken(String userKey) {
        return Constant.USER_ONLINE_TOKENS_PREFIX + userKey;
    }

    private String getUserOnlineToken(String userKey, String clientType) {
        return Constant.USER_ONLINE_TOKENS_PREFIX + clientType + ":" + userKey;
    }

    public void setUserAgent(LoginUser loginUser) {
        setUserAgent(loginUser, true);
    }

    public void setUserAgent(LoginUser loginUser, boolean resolveLocation) {
        UserAgent userAgent = UserAgent.parseUserAgentString(ServletUtil.getRequest().getHeader("User-Agent"));
        String ip = IPUtils.getIp(ServletUtil.getRequest());
        loginUser.setLoginIp(ip);
        if (resolveLocation) {
            try {
                loginUser.setLoginLocation(IpAddressUtil.getRealAddressByIP(loginUser.getLoginIp()));
            } catch (Exception e) {
                loginUser.setLoginLocation("UNKNOWN");
            }
        } else if (StringUtils.isEmpty(loginUser.getLoginLocation())) {
            loginUser.setLoginLocation(IPUtils.internalIp(ip) ? "内网IP" : "");
        }
        loginUser.setOs(userAgent.getOperatingSystem().getName());
        loginUser.setBrowser(userAgent.getBrowser().getName());
    }
}
