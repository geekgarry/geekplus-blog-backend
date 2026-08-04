package com.geekplus.webapp.function.service.impl;

import com.geekplus.webapp.function.mapper.GpArticleCommentMapper;
import com.geekplus.common.util.datetime.DateUtil;
import com.geekplus.webapp.function.entity.GpUserComment;
import com.geekplus.webapp.function.service.IGpArticleCommentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 用户评论回复留言Service业务层处理
 *
 * @author 佚名
 * @date 2023-03-12
 */
@Service
@Slf4j
public class GpArticleCommentServiceImpl implements IGpArticleCommentService
{
    @Autowired
    private GpArticleCommentMapper gpArticleCommentMapper;

    /**
     * 查询用户评论回复留言
     *
     * @param id 用户评论回复留言ID
     * @return 用户评论回复留言
     */
    @Override
    public GpUserComment selectGpArticleCommentById(Long id)
    {
        return gpArticleCommentMapper.selectGpArticleCommentById(id);
    }

    /**
     * 查询用户评论回复留言列表
     *
     * @param gpUserComment 用户评论回复留言,查询列表list
     * @return 用户评论回复留言
     */
    @Override
    public List<GpUserComment> selectGpArticleCommentList(GpUserComment gpUserComment)
    {
        List<GpUserComment> list=gpArticleCommentMapper.selectGpArticleCommentList(gpUserComment);
        // 本人数据流：保持扁平，便于管理端卡片展示每一条自己的评论
        if (gpUserComment.getOwnUserIds() != null && !gpUserComment.getOwnUserIds().isEmpty()) {
            return list;
        }
        return buildTreeList(list);
    }

    /**
      * @Author geekplus
      * @Description //网站用户评论留言，parentId为0，子查询
      * @Param
      * @Throws
      * @Return {@link }
      */
    @Override
    public List<GpUserComment> getArticleComment(GpUserComment gpUserComment) {
        List<GpUserComment> list=gpArticleCommentMapper.getArticleComment(gpUserComment);
        //log.info(list.toString());
        return list;
    }

    /**
      * @Author geekplus
      * @Description //获取网站用户留言的数量
      * @Param
      * @Throws
      * @Return {@link }
      */
    @Override
    public int getArticleCommentCount(String topicId) {
        return gpArticleCommentMapper.getArticleCommentCount(topicId);
    }

    /**
      * @Author geekplus
      * @Description //获取网站用户评论的最新十条数据
      * @Param
      * @Throws
      * @Return {@link }
      */
    @Override
    public List<GpUserComment> getLatestArticleComment() {
        return gpArticleCommentMapper.getLatestArticleComment();
    }

    /**
      * @Author geekplus
      * @Description //获取最热门的六条评论
      * @Param
      * @Throws
      * @Return {@link }
      */
    @Override
    public List<GpUserComment> getHotWebArticleComment() {
        return gpArticleCommentMapper.getHotWebArticleComment();
    }

    /**
      * @Author geekplus
      * @Description // 网站用户留言评论回复
      * @Param
      * @Throws
      * @Return {@link }
      */
    @Override
    public int insertArticleComment(GpUserComment gpUserComment) {
        gpUserComment.setCreateTime(DateUtil.getNowDate());
        return gpArticleCommentMapper.insertArticleComment(gpUserComment);
    }
    /**
     * 新增用户评论回复留言
     *
     * @param gpUserComment 用户评论回复留言
     * @return 结果
     */
    @Override
    public int insertGpArticleComment(GpUserComment gpUserComment)
    {
        gpUserComment.setCreateTime(DateUtil.getNowDate());
        return gpArticleCommentMapper.insertGpArticleComment(gpUserComment);
    }

    /**
     * 修改用户评论回复留言
     *
     * @param gpUserComment 用户评论回复留言
     * @return 结果
     */
    @Override
    public int updateGpArticleComment(GpUserComment gpUserComment)
    {
        return gpArticleCommentMapper.updateGpArticleComment(gpUserComment);
    }

    /**
     * 批量删除用户评论回复留言
     *
     * @param ids 需要删除的用户评论回复留言ID
     * @return 结果
     */
    @Override
    public int deleteGpArticleCommentByIds(Long[] ids)
    {
        return gpArticleCommentMapper.deleteGpArticleCommentByIds(ids);
    }

    /**
     * 删除用户评论回复留言信息
     *
     * @param id 用户评论回复留言ID
     * @return 结果
     */
    @Override
    public int deleteGpArticleCommentById(Long id)
    {
        return gpArticleCommentMapper.deleteGpArticleCommentById(id);
    }

    //构造二树形目录：一次批量查子回复，避免 N+1
    public List<GpUserComment> buildTreeList(List<GpUserComment> listData){
        if (listData == null || listData.isEmpty()) {
            return listData;
        }
        List<Long> parentIds = new ArrayList<>();
        for (GpUserComment item : listData) {
            if (item != null && item.getId() != null) {
                parentIds.add(item.getId());
            }
        }
        if (parentIds.isEmpty()) {
            return listData;
        }
        List<GpUserComment> children = gpArticleCommentMapper.selectChildrenByParentIds(parentIds);
        java.util.Map<Long, List<GpUserComment>> childMap = new java.util.HashMap<>();
        if (children != null) {
            for (GpUserComment c : children) {
                if (c == null || c.getParentId() == null) {
                    continue;
                }
                childMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
            }
        }
        for (GpUserComment item : listData) {
            List<GpUserComment> kids = childMap.get(item.getId());
            item.setChildren(kids == null ? new ArrayList<>() : kids);
        }
        return listData;
    }

    //获取属性结构菜单目录
    public List<GpUserComment> buildTreeGpUserComment(List<GpUserComment> gpUserCommentList){
        List<GpUserComment> gpArticleCategoryList=new ArrayList<>();
        for (GpUserComment item:gpUserCommentList) {
            if(item.getParentId()==0){
                item.setChildren(getChildrenList(gpUserCommentList,item.getId()));
                gpArticleCategoryList.add(item);
            }
        }
        return gpArticleCategoryList;
    }

    //获取当前目录的子菜单
    private List<GpUserComment> getChildrenList(List<GpUserComment> gpUserCommentList, Long id) {
        List<GpUserComment> childrenList=new ArrayList<>();
        for(Iterator<GpUserComment> iterator = gpUserCommentList.iterator(); iterator.hasNext();){
            GpUserComment item = iterator.next();
            if(item.getParentId().equals(id)){
                item.setChildren(getChildrenList(gpUserCommentList,item.getId()));
                childrenList.add(item);
            }
        }
        return childrenList;
    }
}
