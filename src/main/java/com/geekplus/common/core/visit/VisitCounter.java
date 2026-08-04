package com.geekplus.common.core.visit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 全站访问计数：累计 + 日/月/季/年（Redis）
 * 历史明细依赖 gp_site_daily_stats 归档，不必只靠 Redis 永久保存。
 */
@Component
public class VisitCounter {

    public static final String VISIT_COUNT_KEY = "visit_count";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostConstruct
    public void init() {
        if (Objects.isNull(redisTemplate.opsForValue().get(VISIT_COUNT_KEY))) {
            redisTemplate.opsForValue().set(VISIT_COUNT_KEY, "0");
        }
    }

    public long increment() {
        long total = safeIncr(VISIT_COUNT_KEY);
        PeriodKeys keys = periodKeys(new Date());
        safeIncr(keys.day);
        safeIncr(keys.month);
        safeIncr(keys.quarter);
        safeIncr(keys.year);
        // 日 key 与运营日统计对齐；月/季/年保留约 3 年
        redisTemplate.expire(keys.day, 100, TimeUnit.DAYS);
        redisTemplate.expire(keys.month, 400, TimeUnit.DAYS);
        redisTemplate.expire(keys.quarter, 800, TimeUnit.DAYS);
        redisTemplate.expire(keys.year, 1200, TimeUnit.DAYS);
        return total;
    }

    public long getCount() {
        return parseLong(redisTemplate.opsForValue().get(VISIT_COUNT_KEY));
    }

    /** 前台/看板：累计与当前日/月/季/年 */
    public Map<String, Object> getPeriodSnapshot() {
        PeriodKeys keys = periodKeys(new Date());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("visitCount", getCount());
        map.put("today", parseLong(redisTemplate.opsForValue().get(keys.day)));
        map.put("month", parseLong(redisTemplate.opsForValue().get(keys.month)));
        map.put("quarter", parseLong(redisTemplate.opsForValue().get(keys.quarter)));
        map.put("year", parseLong(redisTemplate.opsForValue().get(keys.year)));
        map.put("periodLabels", periodLabels(keys));
        return map;
    }

    private Map<String, String> periodLabels(PeriodKeys keys) {
        Map<String, String> labels = new HashMap<>();
        labels.put("day", keys.dayLabel);
        labels.put("month", keys.monthLabel);
        labels.put("quarter", keys.quarterLabel);
        labels.put("year", keys.yearLabel);
        return labels;
    }

    private long safeIncr(String key) {
        Long v = redisTemplate.opsForValue().increment(key);
        return v == null ? 0L : v;
    }

    private long parseLong(String s) {
        if (s == null || s.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0L;
        }
    }

    static PeriodKeys periodKeys(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH) + 1;
        int q = (m - 1) / 3 + 1;
        String day = new SimpleDateFormat("yyyy-MM-dd").format(date);
        PeriodKeys keys = new PeriodKeys();
        keys.day = "visit:pv:day:" + day;
        keys.month = "visit:pv:month:" + y + "-" + String.format("%02d", m);
        keys.quarter = "visit:pv:quarter:" + y + "-Q" + q;
        keys.year = "visit:pv:year:" + y;
        keys.dayLabel = day;
        keys.monthLabel = y + "-" + String.format("%02d", m);
        keys.quarterLabel = y + "-Q" + q;
        keys.yearLabel = String.valueOf(y);
        return keys;
    }

    static class PeriodKeys {
        String day;
        String month;
        String quarter;
        String year;
        String dayLabel;
        String monthLabel;
        String quarterLabel;
        String yearLabel;
    }
}
