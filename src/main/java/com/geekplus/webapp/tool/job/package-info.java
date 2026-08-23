/**
 * 岗位数据服务：搜索编排、招聘深链、合规爬虫 Worker、岗位库。
 *
 * <pre>
 * controller/   JobSearchController  → /api/job/search · /crawl/run
 * service/      JobSearchService · JobDeepLinkService
 * crawler/      JobSourceClient · RemotiveJobSourceClient · JobCrawlWorker
 * entity/ mapper/ dto/
 * SQL:          resources/db/job_posting.sql
 * </pre>
 *
 * 合规原则：只接入公开 API / 官方开放平台；商业招聘站用深链跳转，不做登录态爬取。
 */
package com.geekplus.webapp.tool.job;
