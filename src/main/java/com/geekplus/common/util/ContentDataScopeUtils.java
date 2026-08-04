package com.geekplus.common.util;

import com.geekplus.common.domain.LoginUser;
import com.geekplus.webapp.system.entity.SysUser;
import com.geekplus.webapp.system.vo.SysRoleVO;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 内容侧数据范围：管理员看全站，普通用户仅本人。
 */
public final class ContentDataScopeUtils {

    private static final List<String> ADMIN_ROLE_KEYS = Arrays.asList(
            "admin", "blog_admin", "site_admin", "website_admin",
            "blogAdmin", "siteAdmin", "webManage", "development"
    );

    private ContentDataScopeUtils() {}

    public static boolean isBlogSiteAdmin(LoginUser user) {
        if (user == null) {
            return false;
        }
        if (SysUser.isAdmin(user.getUserId())) {
            return true;
        }
        Integer userType = user.getUserType();
        if (userType != null && (userType == 1 || userType == 2)) {
            return true;
        }
        List<SysRoleVO> roles = user.getSysRoleList();
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        for (SysRoleVO role : roles) {
            if (role == null) {
                continue;
            }
            String key = role.getRoleKey();
            if (StringUtils.isNotEmpty(key)) {
                for (String adminKey : ADMIN_ROLE_KEYS) {
                    if (adminKey.equalsIgnoreCase(key)) {
                        return true;
                    }
                }
            }
            String name = role.getRoleName();
            if (StringUtils.isNotEmpty(name)
                    && (name.contains("博客管理") || name.contains("网站管理")
                    || name.contains("系统管理") || name.contains("超级管理") || name.contains("站点管理"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 评论 user_id 可能是纯数字或 sysUser:数字
     */
    public static List<String> ownCommentUserIds(LoginUser user) {
        List<String> ids = new ArrayList<>();
        if (user == null || user.getUserId() == null) {
            return ids;
        }
        String uid = String.valueOf(user.getUserId());
        ids.add(uid);
        ids.add("sysUser:" + uid);
        return ids;
    }

    /** 评论是否属于当前用户 */
    public static boolean ownsComment(LoginUser user, String commentUserId) {
        if (isBlogSiteAdmin(user)) {
            return true;
        }
        if (user == null || StringUtils.isEmpty(commentUserId)) {
            return false;
        }
        return ownCommentUserIds(user).contains(commentUserId);
    }

    /** 文章是否属于当前用户（管理员放行） */
    public static boolean ownsArticle(LoginUser user, Long authorId) {
        if (isBlogSiteAdmin(user)) {
            return true;
        }
        if (user == null || user.getUserId() == null || authorId == null) {
            return false;
        }
        return user.getUserId().equals(authorId);
    }
}
