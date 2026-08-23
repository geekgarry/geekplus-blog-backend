package com.geekplus.webapp.tool.resume.service;

import com.geekplus.common.domain.ChatPrompt;
import com.geekplus.common.util.ai.AiJsonParser;
import com.geekplus.webapp.common.service.AiService;
import com.geekplus.webapp.tool.resume.dto.ResumeAnalyzeRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 简历 AI：岗位分析 / 人岗匹配 / 按岗优化。
 * PPT 已迁到 {@link com.geekplus.webapp.tool.ppt.service.PptAiService}。
 */
@Service
public class ResumeAiService {

    private final AiService aiService;
    private final AiJsonParser aiJsonParser;

    public ResumeAiService(AiService aiService, AiJsonParser aiJsonParser) {
        this.aiService = aiService;
        this.aiJsonParser = aiJsonParser;
    }

    public Map<String, Object> analyze(ResumeAnalyzeRequest req) throws Exception {
        if (req == null || !StringUtils.hasText(req.getType())) {
            throw new IllegalArgumentException("type 必填：job | match | generate");
        }
        String type = req.getType().trim().toLowerCase(Locale.ROOT);
        String system;
        StringBuilder user = new StringBuilder();

        if ("job".equals(type)) {
            system = "你是资深 HR 与行业专家。分析岗位要求（文本或附件），提取核心技能、经验、加分项，"
                    + "给出应聘建议与行业简析，并附技术面试题示例与解题思路。"
                    + "关键要求与加分项请用【】标注，便于前端高亮。使用 Markdown。";
            if (StringUtils.hasText(req.getText())) {
                user.append("岗位描述文本：\n").append(req.getText()).append("\n");
            }
        } else if ("match".equals(type)) {
            system = "你是资深 HR 与职业规划师。结合个人简历与目标岗位做【人岗匹配度】分析："
                    + "亮点、短板、面试建议、简历修改建议（含可直接替换的改写）。"
                    + "用【】标注亮点与关键要求。使用 Markdown。";
            user.append("个人简历数据：\n").append(aiJsonParser.toJson(req.getResumeData())).append("\n");
            if (StringUtils.hasText(req.getText())) {
                user.append("目标岗位描述：\n").append(req.getText()).append("\n");
            }
        } else if ("generate".equals(type)) {
            system = "你是资深 HR。根据个人简历与目标岗位，输出优化后的完整简历 JSON，"
                    + "结构必须与输入 resumeData 一致，内容只增不减事实、可润色表达。"
                    + "只返回 JSON，不要 Markdown 代码块或解释。";
            user.append("个人简历数据：\n").append(aiJsonParser.toJson(req.getResumeData())).append("\n");
            if (StringUtils.hasText(req.getText())) {
                user.append("目标岗位描述：\n").append(req.getText()).append("\n");
            }
        } else {
            throw new IllegalArgumentException("不支持的 type：" + req.getType());
        }

        if (!StringUtils.hasText(user.toString()) && !StringUtils.hasText(req.getFileData())) {
            throw new IllegalArgumentException("请提供分析内容（文本或文件）");
        }

        ChatPrompt prompt = new ChatPrompt();
        prompt.setChatMsg(system + "\n\n" + user);
        if (StringUtils.hasText(req.getFileData())) {
            prompt.setMediaData(req.getFileData());
            prompt.setMediaMimeType(StringUtils.hasText(req.getMimeType()) ? req.getMimeType() : "application/octet-stream");
            prompt.setMediaFileName("jd-upload");
        }

        String text = aiService.chat(prompt);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", text);
        out.put("type", type);
        return out;
    }
}
