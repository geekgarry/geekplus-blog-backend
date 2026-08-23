package com.geekplus.common.util.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * AI 返回文本 → JSON 对象的统一解析（去 Markdown 围栏、截取首尾花括号）。
 * 简历分析 / PPT / 岗位洞察等共用，避免各 Service 复制粘贴。
 */
@Component
public class AiJsonParser {

    private final ObjectMapper objectMapper;

    public AiJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> parseObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            return objectMapper.readValue(t, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            int start = t.indexOf('{');
            int end = t.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readValue(t.substring(start, end + 1),
                            new TypeReference<Map<String, Object>>() {});
                } catch (Exception ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    public String toJson(Object obj) {
        if (obj == null) {
            return "{}";
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> asMap(Object obj) {
        if (obj == null) {
            return java.util.Collections.emptyMap();
        }
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        try {
            return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }
}
