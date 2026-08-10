package com.geekplus.webapp.system.service.impl;

import com.geekplus.common.util.sysmenu.SysMenuUtil;
import com.geekplus.webapp.system.mapper.SysMenuMapper;
import com.geekplus.webapp.system.entity.SysMenu;
import com.geekplus.webapp.system.service.SysMenuService;
//import com.geekplus.core.AbstractService;
import com.geekplus.webapp.system.entity.SysRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Resource;


/**
 * Created by CodeGenerator on 2023/06/18.
 */
@Slf4j
@Service
@Transactional
public class SysMenuServiceImpl implements SysMenuService {
    @Resource
    private SysMenuMapper sysMenuMapper;

    @Resource
    private com.geekplus.webapp.system.service.cache.RbacCacheService rbacCacheService;

    private void afterMenuChange() {
        rbacCacheService.evictAllRolesAsync();
    }

    /**
    * 增加
    * @param sysMenu
    * @return 系统菜单权限
    */
    public Integer insertSysMenu(SysMenu sysMenu){
        Integer n = sysMenuMapper.insertSysMenu(sysMenu);
        afterMenuChange();
        return n;
    }

    /**
    * 批量增加
    * @param sysMenuList
    * @return 系统菜单权限
    */
    public Integer batchInsertSysMenuList(List<SysMenu> sysMenuList){
        Integer n = sysMenuMapper.batchInsertSysMenuList(sysMenuList);
        afterMenuChange();
        return n;
    }

    /**
    * 删除
    * @param menuId
    */
    public Integer deleteSysMenuById(Long menuId){
        Integer n = sysMenuMapper.deleteSysMenuById(menuId);
        afterMenuChange();
        return n;
    }

    /**
    * 批量删除
    */
    public Integer deleteSysMenuByIds(Long[] menuIds){
        Integer n = sysMenuMapper.deleteSysMenuByIds(menuIds);
        afterMenuChange();
        return n;
    }

    /**
    * 修改
    * @param sysMenu
    */
    public Integer updateSysMenu(SysMenu sysMenu){
        Integer n = sysMenuMapper.updateSysMenu(sysMenu);
        afterMenuChange();
        return n;
    }

    /**
    * 批量修改某几个字段
    * @param menuIds
    */
    public Integer batchUpdateSysMenuList(Long[] menuIds){
        return sysMenuMapper.batchUpdateSysMenuList(menuIds);
    }

    /**
    * 查询全部
    */
    public List<SysMenu> selectSysMenuList(SysMenu sysMenu){
        return sysMenuMapper.selectSysMenuList(sysMenu);
    }

    /**
     * 查询全部
     */
    public List<SysMenu> selectSysMenuTreeList(){
        return SysMenuUtil.getParentMenuList(sysMenuMapper.selectSysMenuTreeList());
    }

    /**
    * 查询全部,用于联合查询，在此基础做自己的定制改动
    */
    public List<SysMenu> selectUnionSysMenuList(SysMenu sysMenu){
        return sysMenuMapper.selectUnionSysMenuList(sysMenu);
    }

    /**
    * 根据Id查询单条数据
    */
    public SysMenu selectSysMenuById(Long menuId){
        return sysMenuMapper.selectSysMenuById(menuId);
    }

    /**
     * @Author geekplus
     * @Description //根据用户角色sysRoles查询菜单权限permission列表
     */
    @Override
    public List<SysMenu> getSysMenuPermsByRoles(List<SysRole> sysRoles) {
        return sysMenuMapper.selectMenuPermsByRoles(sysRoles);
    }

    /**
     * @Author geekplus
     * @Description //根据userName查询菜单权限permission列表
     */
    @Override
    public List<SysMenu> getMenuTreeByUsername(String userName) {
        return SysMenuUtil.getParentMenuList(sysMenuMapper.selectMenuTreeByUsername(userName));
    }

    /**
     * @Author geekplus
     * @Description //根据userId查询菜单权限permission列表
     */
    @Override
    public List<SysMenu> getMenuPermsByUsername(String userName) {
        return sysMenuMapper.selectMenuPermsByUsername(userName);
    }

    @Override
    public List<SysMenu> getMenuPermsByUserId(Long userId) {
        return sysMenuMapper.selectMenuPermsByUserId(userId);
    }

    @Override
    public List<SysMenu> getMenuTreeByUserId(Long userId) {
        List<SysMenu> sysMenuList=sysMenuMapper.selectMenuTreeByUserId(userId);
        return SysMenuUtil.getParentMenuList(sysMenuList);
    }

    @Override
    public List<SysMenu> getMenuTreeByRoleId(Long roleId) {
        return SysMenuUtil.getParentMenuList(sysMenuMapper.selectMenuTreeByRoleId(roleId));
    }

    @Override
    public List<SysMenu> getMenuListByRoleId(Long roleId) {
        return sysMenuMapper.selectMenuTreeByRoleId(roleId);
    }

    @Override
    public List<Integer> getMenuIdListByRoleId(Long roleId) {
        return sysMenuMapper.selectMenuIdListByRoleId(roleId);
    }

}
