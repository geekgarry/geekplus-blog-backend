package com.geekplus.webapp.system.service.impl;

import com.geekplus.webapp.system.entity.SysRoleDept;
import com.geekplus.webapp.system.mapper.SysRoleDeptMapper;
import com.geekplus.webapp.system.service.SysRoleDeptService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 角色与部门关联实现
 */
@Service
@Transactional
public class SysRoleDeptServiceImpl implements SysRoleDeptService
{
    @Resource
    private SysRoleDeptMapper sysRoleDeptMapper;

    @Override
    public List<Long> selectDeptIdsByRoleId(Long roleId)
    {
        return sysRoleDeptMapper.selectDeptIdsByRoleId(roleId);
    }

    @Override
    public int deleteByRoleId(Long roleId)
    {
        return sysRoleDeptMapper.deleteByRoleId(roleId);
    }

    @Override
    public int batchInsert(List<SysRoleDept> list)
    {
        if (list == null || list.isEmpty())
        {
            return 0;
        }
        return sysRoleDeptMapper.batchInsert(list);
    }

    @Override
    public List<SysRoleDept> selectRolesDepts(SysRoleDept sysRoleDept)
    {
        return sysRoleDeptMapper.selectRolesDepts(sysRoleDept);
    }
}
