package com.geekplus.webapp.function.service.cache;

import com.geekplus.common.cache.TwoLevelCache;
import com.geekplus.common.constant.Constant;
import com.geekplus.webapp.function.entity.GpArticleCategory;
import com.geekplus.webapp.function.mapper.GpArticleCategoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 前台博客导航（文章分类树）双重缓存。
 */
@Slf4j
@Service
public class BlogNavCacheService {

    @Resource
    private TwoLevelCache twoLevelCache;
    @Resource
    private GpArticleCategoryMapper gpArticleCategoryMapper;

    @SuppressWarnings("unchecked")
    public List<GpArticleCategory> getNavTree() {
        List<GpArticleCategory> tree = twoLevelCache.get(Constant.BLOG_NAV_MENU, () -> {
            List<GpArticleCategory> list = gpArticleCategoryMapper.selectSubParentCategory();
            return list != null ? new ArrayList<>(list) : new ArrayList<GpArticleCategory>();
        });
        return tree != null ? tree : Collections.emptyList();
    }

    public void evict() {
        twoLevelCache.evict(Constant.BLOG_NAV_MENU);
        log.debug("已失效博客导航缓存");
    }

    @Async
    public void evictAsync() {
        evict();
    }
}
