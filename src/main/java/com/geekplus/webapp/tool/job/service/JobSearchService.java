package com.geekplus.webapp.tool.job.service;

import com.geekplus.common.domain.ChatPrompt;
import com.geekplus.common.util.ai.AiJsonParser;
import com.geekplus.webapp.common.service.AiService;
import com.geekplus.webapp.tool.job.dto.JobSearchRequest;
import com.geekplus.webapp.tool.job.entity.JobPosting;
import com.geekplus.webapp.tool.job.mapper.JobPostingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 岗位搜索编排：
 * 1) 平台深链（即时可用）
 * 2) 本地岗位库（爬虫 Worker 写入，有则优先）
 * 3) AI 市场洞察（库为空或条数不足时补强）
 */
@Service
public class JobSearchService {

    private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);

    private final JobDeepLinkService deepLinkService;
    private final JobPostingMapper jobPostingMapper;
    private final AiService aiService;
    private final AiJsonParser aiJsonParser;

    public JobSearchService(JobDeepLinkService deepLinkService,
                            JobPostingMapper jobPostingMapper,
                            AiService aiService,
                            AiJsonParser aiJsonParser) {
        this.deepLinkService = deepLinkService;
        this.jobPostingMapper = jobPostingMapper;
        this.aiService = aiService;
        this.aiJsonParser = aiJsonParser;
    }

    public Map<String, Object> search(JobSearchRequest req) throws Exception {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String keyword = trim(req.getKeyword());
        String industry = trim(req.getIndustry());
        String city = trim(req.getCity());
        String experience = trim(req.getExperience());

        if (!StringUtils.hasText(keyword) && req.getResumeData() != null) {
            Map<String, Object> resume = aiJsonParser.asMap(req.getResumeData());
            Object ji = resume.get("jobIntention");
            if (ji instanceof Map) {
                Object tj = ((Map<?, ?>) ji).get("targetJob");
                Object tc = ((Map<?, ?>) ji).get("targetCity");
                if (!StringUtils.hasText(keyword) && tj != null) {
                    keyword = String.valueOf(tj);
                }
                if (!StringUtils.hasText(city) && tc != null) {
                    city = String.valueOf(tc);
                }
            }
        }
        if (!StringUtils.hasText(keyword) && !StringUtils.hasText(industry)) {
            throw new IllegalArgumentException("请至少提供职位或行业关键词");
        }

        String q = StringUtils.hasText(keyword) ? keyword : industry;
        List<Map<String, String>> platformLinks = deepLinkService.buildLinks(q, city);

        List<Map<String, Object>> dbJobs = queryDbJobs(q, city, industry);
        String dataSource = dbJobs.isEmpty() ? "ai+deeplink" : "db+deeplink";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("platformLinks", platformLinks);
        out.put("keyword", q);
        out.put("city", city);
        out.put("industry", industry);
        out.put("dataSource", dataSource);

        // 库内够用则只补短洞察；否则完整 AI jobs
        if (dbJobs.size() >= 5) {
            out.put("jobs", dbJobs);
            out.put("insight", buildShortInsight(q, city, industry, experience, dbJobs.size()));
        } else {
            Map<String, Object> aiPart = askAiInsight(q, city, industry, experience);
            out.put("insight", aiPart.getOrDefault("insight", ""));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> aiJobs = (List<Map<String, Object>>) aiPart.getOrDefault("jobs", Collections.emptyList());
            List<Map<String, Object>> merged = new ArrayList<>(dbJobs);
            for (Object item : aiJobs) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) item;
                    m.putIfAbsent("source", "ai-reference");
                    merged.add(m);
                }
            }
            out.put("jobs", merged);
            if (!dbJobs.isEmpty()) {
                out.put("dataSource", "db+ai+deeplink");
            }
        }
        return out;
    }

    private List<Map<String, Object>> queryDbJobs(String q, String city, String industry) {
        try {
            List<JobPosting> rows = jobPostingMapper.search(q, city, industry, 20);
            List<Map<String, Object>> list = new ArrayList<>();
            if (rows == null) {
                return list;
            }
            for (JobPosting p : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", p.getTitle());
                m.put("company", p.getCompany());
                m.put("city", p.getCity());
                m.put("salary", p.getSalary());
                m.put("summary", p.getSummary());
                m.put("requirements", p.getRequirements());
                m.put("source", p.getSource());
                m.put("url", p.getUrl());
                list.add(m);
            }
            return list;
        } catch (Exception e) {
            // 未执行 job_posting.sql 时降级，不打断搜索
            log.debug("岗位库查询不可用（请执行 db/job_posting.sql）: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> askAiInsight(String q, String city, String industry, String experience)
            throws Exception {
        String aiPrompt = "你是中国互联网招聘市场分析助手。根据检索条件输出 JSON（不要 Markdown）：\n"
                + "{\"insight\":\"Markdown 市场洞察（薪资区间、技能要求、竞争态势，200-400字）\","
                + "\"jobs\":[{\"title\":\"\",\"company\":\"\",\"city\":\"\",\"salary\":\"\","
                + "\"summary\":\"一句话\",\"requirements\":\"要点\",\"source\":\"参考来源\",\"url\":\"可空\"}]}\n"
                + "jobs 给 6-8 条参考岗位（可基于公开市场常识，url 可空；标注 source=ai-reference）。\n"
                + "条件：职位=" + q + "；行业=" + nullToEmpty(industry)
                + "；城市=" + nullToEmpty(city) + "；经验=" + nullToEmpty(experience);
        ChatPrompt prompt = new ChatPrompt();
        prompt.setChatMsg(aiPrompt);
        String raw = aiService.chat(prompt);
        Map<String, Object> parsed = aiJsonParser.parseObject(raw);
        if (parsed != null) {
            return parsed;
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("insight", raw);
        fallback.put("jobs", Collections.emptyList());
        return fallback;
    }

    private String buildShortInsight(String q, String city, String industry, String experience, int n)
            throws Exception {
        String tip = "库内已有 " + n + " 条相关岗位。请用 80～150 字中文简述「" + q + "」"
                + (StringUtils.hasText(city) ? ("@" + city) : "")
                + " 的市场要点（技能/薪资/竞争），Markdown 即可，不要 JSON。";
        ChatPrompt prompt = new ChatPrompt();
        prompt.setChatMsg(tip);
        try {
            return aiService.chat(prompt);
        } catch (Exception e) {
            return "已从岗位库匹配 " + n + " 条结果，可结合上方平台深链查看实时招聘。";
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
