package com.geekplus.webapp.system.service;

import com.geekplus.webapp.system.entity.SysRoleDept;

import java.util.List;

/**
 * 角色与部门关联 Service
 */
public interface SysRoleDeptService
{
    /**
     * 按角色查询部门 ID 列表
     */
    List<Long> selectDeptIdsByRoleId(Long roleId);

    /**
     * 按角色删除全部关联
     */
    int deleteByRoleId(Long roleId);

    /**
     * 批量插入
     */
    int batchInsert(List<SysRoleDept> list);

    /**
     * 条件查询关联
     */
    List<SysRoleDept> selectRolesDepts(SysRoleDept sysRoleDept);
}
