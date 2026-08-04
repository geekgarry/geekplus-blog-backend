package com.geekplus.common.core.scheduled;

import com.geekplus.webapp.function.service.ISiteStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * 每日 00:10 将昨日 Redis 站点统计归档到 gp_site_daily_stats
 */
@Component
public class SiteStatsArchiveTask {

    private static final Logger log = LoggerFactory.getLogger(SiteStatsArchiveTask.class);

    @Autowired
    private ISiteStatsService siteStatsService;

    @Scheduled(cron = "0 10 0 * * ?")
    public void archiveYesterday() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -1);
        String day = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
        log.info("归档站点日统计: {}", day);
        siteStatsService.archiveDay(day);
    }
}
