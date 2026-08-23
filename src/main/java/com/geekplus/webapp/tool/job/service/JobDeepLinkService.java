package com.geekplus.webapp.tool.job.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主流招聘平台「搜索深链」生成（不爬站，仅拼公开搜索 URL）。
 * 与 {@link JobSearchService} 分离，便于单独单测与扩展平台。
 */
@Service
public class JobDeepLinkService {

    public List<Map<String, String>> buildLinks(String keyword, String city) {
        String q = enc(keyword);
        String c = enc(StringUtils.hasText(city) ? city : "");
        List<Map<String, String>> list = new ArrayList<>();
        list.add(link("BOSS直聘",
                "https://www.zhipin.com/web/geek/job?query=" + q
                        + (StringUtils.hasText(city) ? "&city=" + c : ""),
                "打开 BOSS 实时岗位列表"));
        list.add(link("智联招聘",
                "https://sou.zhaopin.com/?jl=&kw=" + q,
                "智联关键词检索"));
        list.add(link("前程无忧",
                "https://search.51job.com/list/000000,000000,0000,00,9,99," + q + ",2,1.html",
                "51job 搜索结果"));
        list.add(link("拉勾",
                "https://www.lagou.com/wn/jobs?kd=" + q,
                "互联网向岗位"));
        list.add(link("猎聘",
                "https://www.liepin.com/zhaopin/?key=" + q,
                "中高端岗位"));
        list.add(link("LinkedIn",
                "https://www.linkedin.com/jobs/search/?keywords=" + q
                        + (StringUtils.hasText(city) ? "&location=" + c : ""),
                "海外/外企岗位"));
        return list;
    }

    private static Map<String, String> link(String name, String url, String hint) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("url", url);
        m.put("hint", hint);
        return m;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s == null ? "" : s;
        }
    }
}
