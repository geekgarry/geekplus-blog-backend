package com.geekplus.webapp.tool.job.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekplus.webapp.tool.job.entity.JobPosting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Remotive 公开远程岗位 API（无需 Key，条款允许聚合展示）。
 * 文档：https://remotive.com/api/remote-jobs
 * 用作合规样例源；国内站请走深链或官方开放平台，勿无头登录爬取。
 */
@Component
public class RemotiveJobSourceClient implements JobSourceClient {

    private static final Logger log = LoggerFactory.getLogger(RemotiveJobSourceClient.class);
    private static final String API = "https://remotive.com/api/remote-jobs";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public RemotiveJobSourceClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceCode() {
        return "remotive";
    }

    @Override
    public List<JobPosting> fetch(String keyword) throws Exception {
        String url = API;
        if (StringUtils.hasText(keyword)) {
            url = API + "?search=" + java.net.URLEncoder.encode(keyword.trim(), "UTF-8");
        }
        String body = restTemplate.getForObject(url, String.class);
        if (!StringUtils.hasText(body)) {
            return java.util.Collections.emptyList();
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode jobs = root.path("jobs");
        List<JobPosting> list = new ArrayList<>();
        if (!jobs.isArray()) {
            return list;
        }
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.DAY_OF_MONTH, 14);
        Date expire = cal.getTime();

        int n = 0;
        for (JsonNode j : jobs) {
            if (n++ >= 80) {
                break;
            }
            JobPosting p = new JobPosting();
            p.setSource(sourceCode());
            p.setSourceId(String.valueOf(j.path("id").asLong()));
            p.setTitle(j.path("title").asText(""));
            p.setCompany(j.path("company_name").asText(""));
            p.setCity("Remote");
            p.setSalary(j.path("salary").asText(""));
            String desc = j.path("description").asText("");
            if (desc.length() > 500) {
                desc = desc.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                if (desc.length() > 400) {
                    desc = desc.substring(0, 400) + "…";
                }
            }
            p.setSummary(desc);
            p.setRequirements(j.path("job_type").asText(""));
            p.setUrl(j.path("url").asText(""));
            p.setIndustry(j.path("category").asText(""));
            JsonNode tags = j.path("tags");
            if (tags.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode t : tags) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(t.asText());
                }
                p.setTags(sb.toString());
            }
            p.setFetchedAt(now);
            p.setExpireAt(expire);
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            if (StringUtils.hasText(p.getTitle()) && StringUtils.hasText(p.getSourceId())) {
                list.add(p);
            }
        }
        log.info("Remotive 拉取 {} 条 keyword={}", list.size(), keyword);
        return list;
    }
}
