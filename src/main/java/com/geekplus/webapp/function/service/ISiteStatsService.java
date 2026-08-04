package com.geekplus.webapp.function.service;

import java.util.Map;

public interface ISiteStatsService {

    /** 记录一次站点访问（PV + UV） */
    void trackVisit(String visitorId);

    /** 文章阅读增量（DB 已更新后调用） */
    void recordArticleView(Long articleId);

    /** 文章点赞增量 */
    void recordArticleLike(Long articleId);

    /** 新增一条评论（网站或文章） */
    void recordNewComment();

    /** 新增一篇文章 */
    void recordNewArticle();

    /** 归档指定日 Redis → DB */
    void archiveDay(String day);

    /** 运营看板数据（近 days 天） */
    Map<String, Object> buildDashboard(int days);
}
