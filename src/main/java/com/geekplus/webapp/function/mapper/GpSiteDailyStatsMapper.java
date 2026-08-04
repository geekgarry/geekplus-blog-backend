package com.geekplus.webapp.function.mapper;

import com.geekplus.webapp.function.entity.GpSiteDailyStats;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface GpSiteDailyStatsMapper {

    int upsert(GpSiteDailyStats stats);

    GpSiteDailyStats selectByDate(@Param("statDate") Date statDate);

    List<GpSiteDailyStats> selectBetween(@Param("begin") Date begin, @Param("end") Date end);

    /** 文章累计阅读/点赞/篇数 */
    Map<String, Object> selectArticleTotals();

    /** 网站留言 + 文章评论总数 */
    Long selectCommentTotal();
}
