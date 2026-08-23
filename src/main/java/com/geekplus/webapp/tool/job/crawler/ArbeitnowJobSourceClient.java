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
 * Arbeitnow 公开岗位 API（欧洲偏多，免费、无需 Key）。
 * https://www.arbeitnow.com/api/job-board-api
 */
@Component
public class ArbeitnowJobSourceClient implements JobSourceClient {

    private static final Logger log = LoggerFactory.getLogger(ArbeitnowJobSourceClient.class);
    private static final String API = "https://www.arbeitnow.com/api/job-board-api";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public ArbeitnowJobSourceClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceCode() {
        return "arbeitnow";
    }

    @Override
    public List<JobPosting> fetch(String keyword) throws Exception {
        String url = API;
        String body = restTemplate.getForObject(url, String.class);
        if (!StringUtils.hasText(body)) {
            return java.util.Collections.emptyList();
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return java.util.Collections.emptyList();
        }
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.DAY_OF_MONTH, 12);
        Date expire = cal.getTime();

        List<JobPosting> list = new ArrayList<>();
        int n = 0;
        for (JsonNode j : data) {
            String title = j.path("title").asText("");
            String company = j.path("company_name").asText("");
            String loc = j.path("location").asText("");
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
                String hay = (title + " " + company + " " + loc + " " + tags).toLowerCase();
                if (!hay.contains(kw)) {
                    continue;
                }
            }
            if (n++ >= 50) {
                break;
            }
            // slug 作稳定 sourceId
            String sid = j.path("slug").asText();
            if (!StringUtils.hasText(sid)) {
                sid = title + "|" + company;
            }
            JobPosting p = new JobPosting();
            p.setSource(sourceCode());
            p.setSourceId(sid.length() > 120 ? sid.substring(0, 120) : sid);
            p.setTitle(title);
            p.setCompany(company);
            p.setCity(StringUtils.hasText(loc) ? loc : "Europe");
            p.setSalary("");
            String summary = desc.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (summary.length() > 400) {
                summary = summary.substring(0, 400) + "…";
            }
            p.setSummary(summary);
            p.setRequirements(tags);
            p.setUrl(j.path("url").asText(""));
            p.setIndustry(j.path("job_types").isArray() && j.path("job_types").size() > 0
                    ? j.path("job_types").get(0).asText("") : tags);
            p.setTags(tags);
            p.setFetchedAt(now);
            p.setExpireAt(expire);
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            list.add(p);
        }
        log.info("Arbeitnow 匹配 {} 条 keyword={}", list.size(), keyword);
        return list;
    }
}
