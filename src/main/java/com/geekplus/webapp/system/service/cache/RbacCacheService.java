package com.geekplus.webapp.system.service.cache;

import com.geekplus.common.cache.TwoLevelCache;
import com.geekplus.common.constant.Constant;
import com.geekplus.common.redis.RedisUtil;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.webapp.system.entity.SysMenu;
import com.geekplus.webapp.system.entity.SysRole;
import com.geekplus.webapp.system.mapper.SysMenuMapper;
import com.geekplus.webapp.system.mapper.SysRoleDeptMapper;
import com.geekplus.webapp.system.mapper.SysRoleMapper;
import com.geekplus.webapp.system.vo.SysRoleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RBAC 公共缓存：按角色只存一份 menus（含按钮）+ perms + depts + data_scope。
 * <p>
 * 数据权限切面应通过 {@link #getRoleDataScope(Long)} / {@link #getRoleDeptIds(Long)} 取值，
 * 勿依赖 LoginUser 会话里可能过期的 role.dataScope。改角色权限后调用 {@link #evictRole(Long)}。
 */
@Slf4j
@Service
public class RbacCacheService {

    @Resource
    private TwoLevelCache twoLevelCache;
    @Resource
    private RedisUtil redisUtil;
    @Resource
    private SysMenuMapper sysMenuMapper;
    @Resource
    private SysRoleDeptMapper sysRoleDeptMapper;
    @Resource
    private SysRoleMapper sysRoleMapper;

    /** 启动完成后后台预热，不阻塞应用就绪与首批登录 */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            com.geekplus.framework.manager.AsyncManager.me().execute(new java.util.TimerTask() {
                @Override
                public void run() {
                    warmUp();
                }
            });
        } catch (Exception e) {
            // 调度器未就绪时同步兜底一次
            warmUp();
        }
    }

    public void warmUp() {
        try {
            List<SysRole> roles = sysRoleMapper.selectSysRoleList(new SysRole());
            if (roles == null || roles.isEmpty()) {
                return;
            }
            int n = 0;
            for (SysRole role : roles) {
                if (role == null || role.getRoleId() == null) {
                    continue;
                }
                if (role.getStatus() != null && role.getStatus() != 0) {
                    continue;
                }
                getRolePerms(role.getRoleId());
                getRoleMenus(role.getRoleId());
                getRoleDeptIds(role.getRoleId());
                getRoleDataScope(role.getRoleId());
                n++;
            }
            log.info("RBAC 缓存预热完成，角色数={}", n);
        } catch (Exception e) {
            log.warn("RBAC 缓存预热跳过: {}", e.getMessage());
        }
    }

    public Set<String> getRolePerms(Long roleId) {
        if (roleId == null) {
            return Collections.emptySet();
        }
        String key = Constant.RBAC_ROLE_PERMS + roleId;
        Set<String> cached = twoLevelCache.get(key, () -> loadPermsFromDb(roleId));
        return cached != null ? cached : Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    public List<SysMenu> getRoleMenus(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        String key = Constant.RBAC_ROLE_MENUS + roleId;
        List<SysMenu> cached = twoLevelCache.get(key, () -> loadMenusFromDb(roleId));
        return cached != null ? cached : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<Long> getRoleDeptIds(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        String key = Constant.RBAC_ROLE_DEPTS + roleId;
        List<Long> cached = twoLevelCache.get(key, () -> {
            List<Long> ids = sysRoleDeptMapper.selectDeptIdsByRoleId(roleId);
            return ids != null ? new ArrayList<>(ids) : new ArrayList<Long>();
        });
        return cached != null ? cached : Collections.emptyList();
    }

    /**
     * 角色数据范围（1~5）。切面应读此处而非 LoginUser 里可能过期的 dataScope。
     */
    public String getRoleDataScope(Long roleId) {
        if (roleId == null) {
            return "";
        }
        String key = Constant.RBAC_ROLE_DATASCOPE + roleId;
        String cached = twoLevelCache.get(key, () -> {
            SysRole role = sysRoleMapper.selectSysRoleById(roleId);
            if (role == null || role.getDataScope() == null) {
                return "";
            }
            return String.valueOf(role.getDataScope()).trim();
        });
        return cached != null ? cached : "";
    }

    public Set<String> resolvePerms(List<SysRoleVO> roles) {
        Set<String> perms = new HashSet<>();
        if (roles == null) {
            return perms;
        }
        for (SysRoleVO role : roles) {
            if (role == null || role.getRoleId() == null) {
                continue;
            }
            perms.addAll(getRolePerms(role.getRoleId()));
        }
        return perms;
    }

    /** 多角色菜单并集（含按钮） */
    public List<SysMenu> resolveMenus(List<SysRoleVO> roles) {
        Map<Long, SysMenu> map = new LinkedHashMap<>();
        if (roles == null) {
            return new ArrayList<>();
        }
        for (SysRoleVO role : roles) {
            if (role == null || role.getRoleId() == null) {
                continue;
            }
            for (SysMenu m : getRoleMenus(role.getRoleId())) {
                if (m != null && m.getMenuId() != null) {
                    map.putIfAbsent(m.getMenuId(), m);
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    /** 路由用：从 menus 过滤掉按钮 B，不另占 Redis key */
    public List<SysMenu> resolveRouteMenus(List<SysRoleVO> roles) {
        return resolveMenus(roles).stream()
                .filter(m -> m.getMenuType() == null || !"B".equals(m.getMenuType()))
                .collect(Collectors.toList());
    }

    public void evictRole(Long roleId) {
        if (evictRoleKeys(roleId)) {
            bumpPermVer();
        }
    }

    /**
     * 批量失效角色缓存；{@code permVer} 只递增一次，避免循环 evict 时版本号抖动。
     */
    public void evictRoles(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        boolean any = false;
        for (Long id : roleIds) {
            if (evictRoleKeys(id)) {
                any = true;
            }
        }
        if (any) {
            bumpPermVer();
        }
    }

    /**
     * 按库回填会话角色上的 dataScope（不预热 menus/perms，由后续 resolve 懒加载）。
     */
    public void syncRoleDataScopes(List<SysRoleVO> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        for (SysRoleVO role : roles) {
            if (role == null || role.getRoleId() == null) {
                continue;
            }
            String scope = getRoleDataScope(role.getRoleId());
            if (StringUtils.isNotEmpty(scope)) {
                role.setDataScope(scope);
            }
        }
    }

    private boolean evictRoleKeys(Long roleId) {
        if (roleId == null) {
            return false;
        }
        twoLevelCache.evict(Constant.RBAC_ROLE_PERMS + roleId);
        twoLevelCache.evict(Constant.RBAC_ROLE_MENUS + roleId);
        twoLevelCache.evict(Constant.RBAC_ROLE_DEPTS + roleId);
        twoLevelCache.evict(Constant.RBAC_ROLE_DATASCOPE + roleId);
        // 兼容清理历史 routes key（若线上曾写入）
        twoLevelCache.evict(Constant.RBAC_ROLE_ROUTE_MENUS + roleId);
        return true;
    }

    public void evictAllRoles() {
        try {
            List<SysRole> roles = sysRoleMapper.selectSysRoleList(new SysRole());
            if (roles != null) {
                for (SysRole role : roles) {
                    if (role != null && role.getRoleId() != null) {
                        evictRoleKeys(role.getRoleId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("evictAllRoles 失败: {}", e.getMessage());
        }
        bumpPermVer();
        twoLevelCache.clearLocal();
    }

    @Async
    public void evictRoleAsync(Long roleId) {
        evictRole(roleId);
    }

    @Async
    public void evictAllRolesAsync() {
        evictAllRoles();
    }

    public long currentPermVer() {
        Object v = redisUtil.get(Constant.RBAC_PERM_VER);
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    private void bumpPermVer() {
        try {
            if (!redisUtil.hasKey(Constant.RBAC_PERM_VER)) {
                redisUtil.set(Constant.RBAC_PERM_VER, 1L);
            } else {
                redisUtil.incr(Constant.RBAC_PERM_VER, 1);
            }
        } catch (Exception ignored) {
        }
    }

    private Set<String> loadPermsFromDb(Long roleId) {
        List<String> raw = sysMenuMapper.selectPermsByRoleId(roleId);
        Set<String> set = new HashSet<>();
        if (raw == null) {
            return set;
        }
        for (String perm : raw) {
            if (StringUtils.isEmpty(perm)) {
                continue;
            }
            set.addAll(Arrays.asList(perm.trim().split(",")));
        }
        return set.stream().filter(StringUtils::isNotEmpty).collect(Collectors.toCollection(HashSet::new));
    }

    private List<SysMenu> loadMenusFromDb(Long roleId) {
        List<SysMenu> list = sysMenuMapper.selectMenusByRoleId(roleId);
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }
}
