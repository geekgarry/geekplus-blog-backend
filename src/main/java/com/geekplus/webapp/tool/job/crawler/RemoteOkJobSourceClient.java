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
 * RemoteOK 公开 JSON（无需 Key）。
 * https://remoteok.com/api — 聚合远程岗位，注意礼貌限速。
 */
@Component
public class RemoteOkJobSourceClient implements JobSourceClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteOkJobSourceClient.class);
    private static final String API = "https://remoteok.com/api";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public RemoteOkJobSourceClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceCode() {
        return "remoteok";
    }

    @Override
    public List<JobPosting> fetch(String keyword) throws Exception {
        // RemoteOK 全量列表较大，客户端侧按关键词过滤
        String body = restTemplate.getForObject(API, String.class);
        if (!StringUtils.hasText(body)) {
            return java.util.Collections.emptyList();
        }
        JsonNode root = objectMapper.readTree(body);
        if (!root.isArray()) {
            return java.util.Collections.emptyList();
        }
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.DAY_OF_MONTH, 10);
        Date expire = cal.getTime();

        List<JobPosting> list = new ArrayList<>();
        int n = 0;
        for (JsonNode j : root) {
            // 首元素常为法律声明对象，无 id
            if (!j.has("id") || !j.has("position")) {
                continue;
            }
            String title = j.path("position").asText("");
            String company = j.path("company").asText("");
            String desc = j.path("description").asText("");
            String tags = "";
            if (j.path("tags").isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode t : j.path("tags")) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(t.asText());
                }
                tags = sb.toString();
            }
            if (StringUtils.hasText(kw)) {
                String hay = (title + " " + company + " " + tags + " " + desc).toLowerCase();
                if (!hay.contains(kw)) {
                    continue;
                }
            }
            if (n++ >= 60) {
                break;
            }
            JobPosting p = new JobPosting();
            p.setSource(sourceCode());
            p.setSourceId(String.valueOf(j.path("id").asLong()));
            p.setTitle(title);
            p.setCompany(company);
            p.setCity("Remote");
            p.setSalary(j.path("salary").asText(""));
            String summary = desc.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (summary.length() > 400) {
                summary = summary.substring(0, 400) + "…";
            }
            p.setSummary(summary);
            p.setRequirements(tags);
            p.setUrl(j.path("url").asText(j.path("apply_url").asText("")));
            p.setIndustry(tags);
            p.setTags(tags);
            p.setFetchedAt(now);
            p.setExpireAt(expire);
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            list.add(p);
        }
        log.info("RemoteOK 匹配 {} 条 keyword={}", list.size(), keyword);
        return list;
    }
}
