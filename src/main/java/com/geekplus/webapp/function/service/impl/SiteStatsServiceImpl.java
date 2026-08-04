package com.geekplus.webapp.function.service.impl;

import com.geekplus.common.core.visit.VisitCounter;
import com.geekplus.common.redis.RedisUtil;
import com.geekplus.webapp.function.entity.GpSiteDailyStats;
import com.geekplus.webapp.function.mapper.GpArticlesMapper;
import com.geekplus.webapp.function.mapper.GpSiteDailyStatsMapper;
import com.geekplus.webapp.function.service.ISiteStatsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class SiteStatsServiceImpl implements ISiteStatsService {

    private static final long REDIS_TTL_DAYS = 90L;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private GpSiteDailyStatsMapper siteDailyStatsMapper;

    @Autowired
    private GpArticlesMapper gpArticlesMapper;

    @Autowired(required = false)
    private VisitCounter visitCounter;

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    private String keyPv(String day) {
        return "stats:pv:" + day;
    }

    private String keyUv(String day) {
        return "stats:uv:" + day;
    }

    private String keyViews(String day) {
        return "stats:views:" + day;
    }

    private String keyLikes(String day) {
        return "stats:likes:" + day;
    }

    private String keyComments(String day) {
        return "stats:comments:" + day;
    }

    private String keyArticles(String day) {
        return "stats:articles:" + day;
    }

    private void touchTtl(String... keys) {
        long seconds = TimeUnit.DAYS.toSeconds(REDIS_TTL_DAYS);
        for (String k : keys) {
            redisUtil.expire(k, seconds);
        }
    }

    @Override
    public void trackVisit(String visitorId) {
        String day = today();
        String pvKey = keyPv(day);
        String uvKey = keyUv(day);
        redisUtil.incr(pvKey, 1);
        if (StringUtils.isNotEmpty(visitorId)) {
            redisUtil.pfAdd(uvKey, visitorId);
        }
        touchTtl(pvKey, uvKey);
    }

    @Override
    public void recordArticleView(Long articleId) {
        String day = today();
        String key = keyViews(day);
        redisUtil.incr(key, 1);
        touchTtl(key);
    }

    @Override
    public void recordArticleLike(Long articleId) {
        String day = today();
        String key = keyLikes(day);
        redisUtil.incr(key, 1);
        touchTtl(key);
    }

    @Override
    public void recordNewComment() {
        String day = today();
        String key = keyComments(day);
        redisUtil.incr(key, 1);
        touchTtl(key);
    }

    @Override
    public void recordNewArticle() {
        String day = today();
        String key = keyArticles(day);
        redisUtil.incr(key, 1);
        touchTtl(key);
    }

    private long redisLong(String key) {
        Object v = redisUtil.get(key);
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    @Override
    public void archiveDay(String day) {
        if (StringUtils.isEmpty(day)) {
            return;
        }
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd").parse(day);
            GpSiteDailyStats row = new GpSiteDailyStats();
            row.setStatDate(date);
            row.setPv(redisLong(keyPv(day)));
            row.setUv(redisUtil.pfCount(keyUv(day)));
            row.setNewViews(redisLong(keyViews(day)));
            row.setNewLikes(redisLong(keyLikes(day)));
            row.setNewComments(redisLong(keyComments(day)));
            row.setNewArticles((int) redisLong(keyArticles(day)));
            siteDailyStatsMapper.upsert(row);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Map<String, Object> buildDashboard(int days) {
        if (days < 1) {
            days = 30;
        }
        if (days > 90) {
            days = 90;
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date end = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1));
        Date begin = cal.getTime();

        Map<String, GpSiteDailyStats> dbMap = new HashMap<>();
        List<GpSiteDailyStats> dbRows = siteDailyStatsMapper.selectBetween(begin, end);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (dbRows != null) {
            for (GpSiteDailyStats r : dbRows) {
                if (r.getStatDate() != null) {
                    dbMap.put(sdf.format(r.getStatDate()), r);
                }
            }
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        Calendar cursor = Calendar.getInstance();
        cursor.setTime(begin);
        String today = today();
        while (!cursor.getTime().after(end)) {
            String day = sdf.format(cursor.getTime());
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", day);
            point.put("label", (cursor.get(Calendar.MONTH) + 1) + "/" + cursor.get(Calendar.DAY_OF_MONTH));

            long pv;
            long uv;
            long newViews;
            long newLikes;
            long newComments;
            long newArticles;

            // 当日优先 Redis；历史优先 DB，缺失再用 Redis
            if (day.equals(today) || !dbMap.containsKey(day)) {
                pv = redisLong(keyPv(day));
                uv = redisUtil.pfCount(keyUv(day));
                newViews = redisLong(keyViews(day));
                newLikes = redisLong(keyLikes(day));
                newComments = redisLong(keyComments(day));
                newArticles = redisLong(keyArticles(day));
                if (!day.equals(today) && dbMap.containsKey(day)) {
                    GpSiteDailyStats d = dbMap.get(day);
                    if (pv == 0) pv = nvl(d.getPv());
                    if (uv == 0) uv = nvl(d.getUv());
                    if (newViews == 0) newViews = nvl(d.getNewViews());
                    if (newLikes == 0) newLikes = nvl(d.getNewLikes());
                    if (newComments == 0) newComments = nvl(d.getNewComments());
                    if (newArticles == 0) newArticles = d.getNewArticles() == null ? 0 : d.getNewArticles();
                }
            } else {
                GpSiteDailyStats d = dbMap.get(day);
                pv = nvl(d.getPv());
                uv = nvl(d.getUv());
                newViews = nvl(d.getNewViews());
                newLikes = nvl(d.getNewLikes());
                newComments = nvl(d.getNewComments());
                newArticles = d.getNewArticles() == null ? 0 : d.getNewArticles();
            }

            point.put("pv", pv);
            point.put("uv", uv);
            point.put("newViews", newViews);
            point.put("newLikes", newLikes);
            point.put("newComments", newComments);
            point.put("newArticles", newArticles);
            trend.add(point);
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }

        Map<String, Object> totals = siteDailyStatsMapper.selectArticleTotals();
        if (totals == null) {
            totals = new HashMap<>();
        }
        Long commentTotal = siteDailyStatsMapper.selectCommentTotal();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayPv", redisLong(keyPv(today)));
        result.put("todayUv", redisUtil.pfCount(keyUv(today)));
        result.put("todayViews", redisLong(keyViews(today)));
        result.put("todayLikes", redisLong(keyLikes(today)));
        result.put("todayComments", redisLong(keyComments(today)));
        result.put("totalViews", toLong(totals.get("totalViews")));
        result.put("totalLikes", toLong(totals.get("totalLikes")));
        result.put("totalArticles", toLong(totals.get("totalArticles")));
        result.put("totalComments", commentTotal == null ? 0L : commentTotal);
        result.put("trend", trend);

        if (visitCounter != null) {
            result.put("visitPeriod", visitCounter.getPeriodSnapshot());
        }

        // 最近文章（供看板列表）
        try {
            com.geekplus.webapp.function.entity.GpArticles q = new com.geekplus.webapp.function.entity.GpArticles();
            List<com.geekplus.webapp.function.entity.GpArticles> recent =
                    gpArticlesMapper.selectGpArticlesList(q);
            if (recent != null && recent.size() > 6) {
                recent = recent.subList(0, 6);
            }
            result.put("recentArticles", recent == null ? Collections.emptyList() : recent);

            // 分类占比
            Map<String, Integer> catMap = new LinkedHashMap<>();
            List<com.geekplus.webapp.function.entity.GpArticles> all =
                    gpArticlesMapper.selectGpArticlesList(new com.geekplus.webapp.function.entity.GpArticles());
            if (all != null) {
                for (com.geekplus.webapp.function.entity.GpArticles a : all) {
                    String name = "未分类";
                    if (a.getCategory() != null && StringUtils.isNotEmpty(a.getCategory().getCategoryName())) {
                        name = a.getCategory().getCategoryName();
                    } else if (a.getArticleCategory() != null) {
                        name = "分类#" + a.getArticleCategory();
                    }
                    catMap.put(name, catMap.getOrDefault(name, 0) + 1);
                }
            }
            int sum = 0;
            for (int c : catMap.values()) sum += c;
            List<Map<String, Object>> ratio = new ArrayList<>();
            for (Map.Entry<String, Integer> e : catMap.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", e.getKey());
                item.put("count", e.getValue());
                item.put("percent", sum == 0 ? 0 : Math.round(e.getValue() * 100f / sum));
                ratio.add(item);
            }
            ratio.sort((a, b) -> Integer.compare((Integer) b.get("count"), (Integer) a.get("count")));
            if (ratio.size() > 5) {
                ratio = ratio.subList(0, 5);
            }
            result.put("categoryRatio", ratio);
        } catch (Exception e) {
            result.put("recentArticles", Collections.emptyList());
            result.put("categoryRatio", Collections.emptyList());
        }

        return result;
    }

    private long nvl(Long v) {
        return v == null ? 0L : v;
    }

    private long toLong(Object o) {
        if (o == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return 0L;
        }
    }
}
