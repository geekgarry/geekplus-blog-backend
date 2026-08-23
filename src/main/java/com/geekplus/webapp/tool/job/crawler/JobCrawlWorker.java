package com.geekplus.webapp.tool.job.crawler;

import com.geekplus.webapp.tool.job.entity.JobPosting;
import com.geekplus.webapp.tool.job.mapper.JobPostingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 岗位爬虫 Worker：多公开源入库。
 * Remotive 按关键词检索；RemoteOK / Arbeitnow 全量拉取一次（源侧无稳定搜索参）。
 */
@Component
public class JobCrawlWorker {

    private static final Logger log = LoggerFactory.getLogger(JobCrawlWorker.class);

    private final List<JobSourceClient> sources;
    private final JobPostingMapper jobPostingMapper;

    @Value("${geekplus.job.crawl.enabled:false}")
    private boolean enabled;

    @Value("${geekplus.job.crawl.keywords:frontend,java,product manager}")
    private String keywords;

    public JobCrawlWorker(List<JobSourceClient> sources, JobPostingMapper jobPostingMapper) {
        this.sources = sources;
        this.jobPostingMapper = jobPostingMapper;
    }

    @Scheduled(cron = "${geekplus.job.crawl.cron:0 20 */6 * * ?}")
    public void scheduledFetch() {
        if (!enabled) {
            return;
        }
        runOnce();
    }

    public int runOnce() {
        if (sources == null || sources.isEmpty()) {
            return 0;
        }
        int saved = 0;
        String[] kws = (keywords == null ? "" : keywords).split("[,，]");
        try {
            for (JobSourceClient client : sources) {
                String code = client.sourceCode();
                // remotive 支持 search；其它板全量一次，避免重复打满量接口
                if ("remotive".equals(code)) {
                    for (String kw : kws) {
                        String k = kw.trim();
                        if (k.isEmpty()) {
                            continue;
                        }
                        saved += ingest(client, k);
                        Thread.sleep(800L);
                    }
                } else {
                    saved += ingest(client, "");
                    Thread.sleep(1200L);
                }
            }
            try {
                jobPostingMapper.deleteExpired(new Date());
            } catch (Exception e) {
                log.debug("清理过期岗位失败（可能未建表）: {}", e.getMessage());
            }
            log.info("JobCrawlWorker 完成，写入约 {} 条", saved);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        return saved;
    }

    private int ingest(JobSourceClient client, String keyword) {
        int saved = 0;
        try {
            List<JobPosting> batch = client.fetch(keyword);
            for (JobPosting p : batch) {
                try {
                    jobPostingMapper.upsert(p);
                    saved++;
                } catch (Exception ex) {
                    log.debug("upsert skip: {}", ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("JobSource {} keyword={} 失败: {}", client.sourceCode(), keyword, e.getMessage());
        }
        return saved;
    }
}
