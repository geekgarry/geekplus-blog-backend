package com.geekplus.webapp.tool.job.crawler;

import com.geekplus.webapp.tool.job.entity.JobPosting;

import java.util.List;

/**
 * 岗位数据源适配器：只对接「合规公开 API / 自有数据」，禁止登录态爬取商业站。
 */
public interface JobSourceClient {

    /** 来源编码，写入 job_posting.source */
    String sourceCode();

    /**
     * 拉取一批岗位。
     * @param keyword 可选关键词过滤（有的源不支持则全量后由库内搜）
     */
    List<JobPosting> fetch(String keyword) throws Exception;
}
