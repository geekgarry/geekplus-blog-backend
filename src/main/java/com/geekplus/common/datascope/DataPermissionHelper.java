package com.geekplus.common.datascope;

import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.security.AdminAuthUtils;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.framework.aspect.DataScopeAspect;
import com.geekplus.webapp.system.entity.SysDept;
import com.geekplus.webapp.system.mapper.SysDeptMapper;
import com.geekplus.webapp.system.service.cache.RbacCacheService;
import com.geekplus.webapp.system.vo.SysDeptVO;
import com.geekplus.webapp.system.vo.SysRoleVO;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据权限辅助：写操作校验、可见部门 ID、部门树裁剪。
 * <p>
 * 列表过滤由 {@link DataScopeAspect} + MyBatis 完成；此处负责「单条鉴权」与「部门选择」场景。
 */
@Component
public class DataPermissionHelper
{
    @Resource
    private RbacCacheService rbacCacheService;
    @Resource
    private SysDeptMapper sysDeptMapper;

    /**
     * 是否超级管理员（仅 userId=1）：跳过行级数据权限。
     * 普通管理员角色请改 {@code data_scope}，不要依赖本方法。
     */
    public boolean isAdmin(LoginUser user)
    {
        return AdminAuthUtils.isDataScopeBypass(user);
    }

    /**
     * 当前用户是否可访问目标用户（按目标 userId / deptId 与角色 data_scope 判断）。
     */
    public boolean canAccessUser(LoginUser loginUser, Long targetUserId, Long targetDeptId)
    {
        if (loginUser == null)
        {
            return false;
        }
        if (isAdmin(loginUser))
        {
            return true;
        }
        // 无部门限制时：至少本人可访问自己
        if (targetUserId != null && targetUserId.equals(loginUser.getUserId()))
        {
            // 仍走角色判断：SELF/DEPT 等可能允许；此处不短路，下面循环处理
        }
        List<SysRoleVO> roles = loginUser.getSysRoleList();
        if (roles == null || roles.isEmpty())
        {
            return false;
        }
        Long myDeptId = resolveMyDeptId(loginUser);
        for (SysRoleVO role : roles)
        {
            String scope = resolveScope(role);
            if (DataScopeAspect.DATA_SCOPE_ALL.equals(scope))
            {
                return true;
            }
            if (DataScopeAspect.DATA_SCOPE_SELF.equals(scope))
            {
                if (targetUserId != null && targetUserId.equals(loginUser.getUserId()))
                {
                    return true;
                }
            }
            else if (DataScopeAspect.DATA_SCOPE_DEPT.equals(scope))
            {
                if (myDeptId != null && myDeptId.equals(targetDeptId))
                {
                    return true;
                }
            }
            else if (DataScopeAspect.DATA_SCOPE_DEPT_AND_CHILD.equals(scope))
            {
                if (myDeptId != null && targetDeptId != null
                    && (myDeptId.equals(targetDeptId) || isDeptInSubtree(myDeptId, targetDeptId)))
                {
                    return true;
                }
            }
            else if (DataScopeAspect.DATA_SCOPE_CUSTOM.equals(scope))
            {
                List<Long> ids = rbacCacheService.getRoleDeptIds(role.getRoleId());
                if (ids != null && targetDeptId != null && ids.contains(targetDeptId))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 新增/修改用户时：所选部门是否在当前登录者可见范围内。
     */
    public boolean canAccessDept(LoginUser loginUser, Long deptId)
    {
        if (loginUser == null || deptId == null)
        {
            return false;
        }
        if (isAdmin(loginUser))
        {
            return true;
        }
        Set<Long> visible = resolveVisibleDeptIds(loginUser);
        // null 表示不限制（理论上仅 admin）；空集合表示无可见部门
        if (visible == null)
        {
            return true;
        }
        return visible.contains(deptId);
    }

    /**
     * 计算当前用户可见部门 ID 集合。
     *
     * @return {@code null} 表示不限制（管理员）；非 null 为空则无可选部门
     */
    public Set<Long> resolveVisibleDeptIds(LoginUser loginUser)
    {
        if (loginUser == null)
        {
            return Collections.emptySet();
        }
        if (isAdmin(loginUser))
        {
            return null;
        }
        List<SysRoleVO> roles = loginUser.getSysRoleList();
        if (roles == null || roles.isEmpty())
        {
            return Collections.emptySet();
        }
        Set<Long> ids = new HashSet<>();
        Long myDeptId = resolveMyDeptId(loginUser);
        boolean hasSelfOnly = false;
        for (SysRoleVO role : roles)
        {
            String scope = resolveScope(role);
            if (DataScopeAspect.DATA_SCOPE_ALL.equals(scope))
            {
                return null;
            }
            if (DataScopeAspect.DATA_SCOPE_CUSTOM.equals(scope))
            {
                List<Long> custom = rbacCacheService.getRoleDeptIds(role.getRoleId());
                if (custom != null)
                {
                    ids.addAll(custom);
                }
            }
            else if (DataScopeAspect.DATA_SCOPE_DEPT.equals(scope))
            {
                if (myDeptId != null)
                {
                    ids.add(myDeptId);
                }
            }
            else if (DataScopeAspect.DATA_SCOPE_DEPT_AND_CHILD.equals(scope))
            {
                if (myDeptId != null)
                {
                    ids.add(myDeptId);
                    List<SysDept> children = sysDeptMapper.selectChildrenDeptById(myDeptId);
                    if (children != null)
                    {
                        for (SysDept c : children)
                        {
                            if (c.getDeptId() != null)
                            {
                                ids.add(c.getDeptId());
                            }
                        }
                    }
                }
            }
            else if (DataScopeAspect.DATA_SCOPE_SELF.equals(scope))
            {
                hasSelfOnly = true;
                if (myDeptId != null)
                {
                    ids.add(myDeptId);
                }
            }
        }
        // 多角色并集；若只有 SELF 且无部门，返回空（不能选部门建用户）
        if (ids.isEmpty() && hasSelfOnly)
        {
            return Collections.emptySet();
        }
        return ids;
    }

    /**
     * 按可见部门 ID 裁剪部门树（保留祖先路径，便于 treeselect 展示）。
     * allowedIds == null 表示不裁剪。
     */
    public List<SysDept> filterDeptTree(List<SysDept> tree, Set<Long> allowedIds)
    {
        if (tree == null || tree.isEmpty() || allowedIds == null)
        {
            return tree;
        }
        if (allowedIds.isEmpty())
        {
            return new ArrayList<>();
        }
        List<SysDept> result = new ArrayList<>();
        for (SysDept node : tree)
        {
            SysDept kept = filterNode(node, allowedIds);
            if (kept != null)
            {
                result.add(kept);
            }
        }
        return result;
    }

    private SysDept filterNode(SysDept node, Set<Long> allowedIds)
    {
        if (node == null)
        {
            return null;
        }
        List<SysDept> childKept = new ArrayList<>();
        if (node.getChildren() != null)
        {
            for (SysDept child : node.getChildren())
            {
                SysDept c = filterNode(child, allowedIds);
                if (c != null)
                {
                    childKept.add(c);
                }
            }
        }
        boolean selfOk = node.getDeptId() != null && allowedIds.contains(node.getDeptId());
        if (!selfOk && childKept.isEmpty())
        {
            return null;
        }
        node.setChildren(childKept);
        return node;
    }

    private String resolveScope(SysRoleVO role)
    {
        if (role == null)
        {
            return DataScopeAspect.DATA_SCOPE_SELF;
        }
        String scope = rbacCacheService.getRoleDataScope(role.getRoleId());
        if (StringUtils.isEmpty(scope) && StringUtils.isNotEmpty(role.getDataScope()))
        {
            scope = role.getDataScope();
        }
        if (StringUtils.isEmpty(scope))
        {
            scope = DataScopeAspect.DATA_SCOPE_SELF;
        }
        return scope;
    }

    private Long resolveMyDeptId(LoginUser loginUser)
    {
        if (loginUser == null)
        {
            return null;
        }
        SysDeptVO dept = loginUser.getSysDept();
        if (dept != null && dept.getDeptId() != null)
        {
            return dept.getDeptId();
        }
        return loginUser.getDeptId();
    }

    /** 判断 child 是否在 parent 部门树下（ancestors 含 parentId） */
    private boolean isDeptInSubtree(Long parentId, Long childId)
    {
        try
        {
            SysDept child = sysDeptMapper.selectSysDeptById(childId);
            if (child == null || StringUtils.isEmpty(child.getAncestors()))
            {
                return false;
            }
            String ancestors = "," + child.getAncestors() + ",";
            return ancestors.contains("," + parentId + ",");
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
