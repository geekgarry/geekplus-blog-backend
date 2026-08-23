package com.geekplus.webapp.tool.job.controller;

import com.geekplus.common.domain.Result;
import com.geekplus.webapp.tool.job.crawler.JobCrawlWorker;
import com.geekplus.webapp.tool.job.dto.JobSearchRequest;
import com.geekplus.webapp.tool.job.service.JobSearchService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 岗位数据服务入口：搜索 + 可选触发爬虫 Worker。
 */
@RestController
@RequestMapping("/api/job")
public class JobSearchController {

    private final JobSearchService jobSearchService;
    private final JobCrawlWorker jobCrawlWorker;

    public JobSearchController(JobSearchService jobSearchService, JobCrawlWorker jobCrawlWorker) {
        this.jobSearchService = jobSearchService;
        this.jobCrawlWorker = jobCrawlWorker;
    }

    /** 岗位搜索：库内岗位 + 平台深链 + AI 洞察 */
    @PostMapping("/search")
    public Result search(@RequestBody JobSearchRequest request) {
        try {
            return Result.success(jobSearchService.search(request));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 手动触发一次合规源拉取（需 geekplus.job.crawl.enabled=true 且已建表）。
     * 管理端可后续加权限；当前返回写入条数估计。
     */
    @PostMapping("/crawl/run")
    public Result runCrawl() {
        int n = jobCrawlWorker.runOnce();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("upsertedApprox", n);
        data.put("hint", "若为 0：检查 geekplus.job.crawl.enabled 与 db/job_posting.sql 是否已执行");
        return Result.success(data);
    }
}
