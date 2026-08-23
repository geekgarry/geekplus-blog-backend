package com.geekplus.webapp.tool.ppt.service;

import com.geekplus.common.domain.ChatPrompt;
import com.geekplus.common.util.ai.AiJsonParser;
import com.geekplus.webapp.common.service.AiService;
import com.geekplus.webapp.tool.ppt.dto.PptGenerateRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 独立 PPT AI 服务（不再挂在 resume 包下）。
 * 支持：简历 JSON、手输文稿、上传文档抽字 / 多模态附件。
 */
@Service
public class PptAiService {

    private final AiService aiService;
    private final AiJsonParser aiJsonParser;

    public PptAiService(AiService aiService, AiJsonParser aiJsonParser) {
        this.aiService = aiService;
        this.aiJsonParser = aiJsonParser;
    }

    public Map<String, Object> generate(PptGenerateRequest req) throws Exception {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String source = StringUtils.hasText(req.getSourceType())
                ? req.getSourceType().trim().toLowerCase(Locale.ROOT)
                : inferSource(req);
        String title = StringUtils.hasText(req.getTitle()) ? req.getTitle() : "演示文稿";
        String theme = StringUtils.hasText(req.getTheme()) ? req.getTheme() : "mist";
        String extra = StringUtils.hasText(req.getPrompt()) ? ("\n附加要求：" + req.getPrompt()) : "";

        StringBuilder material = new StringBuilder();
        if ("resume".equals(source)) {
            if (req.getResumeData() == null) {
                throw new IllegalArgumentException("resume 模式需要 resumeData");
            }
            material.append("【来源：简历 JSON】\n").append(aiJsonParser.toJson(req.getResumeData()));
        } else if ("text".equals(source)) {
            if (!StringUtils.hasText(req.getText())) {
                throw new IllegalArgumentException("text 模式需要 text");
            }
            material.append("【来源：用户手输文稿】\n").append(req.getText().trim());
        } else if ("file".equals(source)) {
            if (StringUtils.hasText(req.getFileText())) {
                material.append("【来源：上传文档抽字")
                        .append(StringUtils.hasText(req.getFileName()) ? (" · " + req.getFileName()) : "")
                        .append("】\n")
                        .append(clip(req.getFileText(), 28000));
            } else if (!StringUtils.hasText(req.getMediaData())) {
                throw new IllegalArgumentException("file 模式需要 fileText 或 mediaData");
            } else {
                material.append("【来源：上传附件，请从附件识别并整理成演示文稿】")
                        .append(StringUtils.hasText(req.getFileName()) ? (" 文件名=" + req.getFileName()) : "");
            }
        } else {
            throw new IllegalArgumentException("sourceType 仅支持 resume | text | file");
        }

        String aiPrompt = "你是专业演示文稿策划。根据材料生成 PPT JSON（不要 Markdown 围栏）：\n"
                + "{\"title\":\"" + title + "\",\"theme\":\"" + theme + "\","
                + "\"slides\":[{\"title\":\"\",\"content\":\"多行要点，换行分条\","
                + "\"notes\":\"演讲者备注\","
                + "\"layout\":\"title|bullets|two-column|quote|section\","
                + "\"transition\":\"fade|slide|zoom|none\","
                + "\"backgroundColor\":\"\",\"textColor\":\"\",\"accentColor\":\"\"}]}\n"
                + "要求：6～12 页；含封面、目录、主体、总结；layout/transition/notes 尽量填写；"
                + "配色贴合 theme=" + theme + "（纸感莫兰迪：雾青绿/暖灰，避免高饱和霓虹）。"
                + extra + "\n\n材料：\n" + material;

        ChatPrompt prompt = new ChatPrompt();
        prompt.setChatMsg(aiPrompt);
        if (StringUtils.hasText(req.getMediaData())) {
            prompt.setMediaData(req.getMediaData());
            prompt.setMediaMimeType(StringUtils.hasText(req.getMediaMimeType())
                    ? req.getMediaMimeType() : "application/octet-stream");
            prompt.setMediaFileName(StringUtils.hasText(req.getFileName()) ? req.getFileName() : "upload");
        }

        String raw = aiService.chat(prompt);
        Map<String, Object> parsed = aiJsonParser.parseObject(raw);
        if (parsed == null) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("title", title);
            fallback.put("theme", theme);
            fallback.put("slides", Collections.emptyList());
            fallback.put("raw", raw);
            return fallback;
        }
        if (!parsed.containsKey("title")) {
            parsed.put("title", title);
        }
        if (!parsed.containsKey("theme")) {
            parsed.put("theme", theme);
        }
        return parsed;
    }

    private static String inferSource(PptGenerateRequest req) {
        if (req.getResumeData() != null) {
            return "resume";
        }
        if (StringUtils.hasText(req.getFileText()) || StringUtils.hasText(req.getMediaData())) {
            return "file";
        }
        return "text";
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "\n…(正文已截断)";
    }
}
