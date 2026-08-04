package com.geekplus.webapp.system.mapper;

import com.geekplus.webapp.system.entity.SysRoleDept;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色与部门关联 Mapper
 */
public interface SysRoleDeptMapper
{
    /**
     * 按角色查询已勾选部门 ID
     */
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 按角色删除关联
     */
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量插入角色-部门
     */
    int batchInsert(@Param("list") List<SysRoleDept> list);

    /**
     * 查询角色部门关联列表（可选）
     */
    List<SysRoleDept> selectRolesDepts(SysRoleDept sysRoleDept);
}
