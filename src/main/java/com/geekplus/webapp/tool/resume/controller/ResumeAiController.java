package com.geekplus.webapp.tool.resume.controller;

import com.geekplus.common.domain.ChatPrompt;
import com.geekplus.common.domain.Result;
import com.geekplus.common.dto.AIRequest;
import com.geekplus.webapp.common.service.AiService;
import com.geekplus.webapp.tool.job.dto.JobSearchRequest;
import com.geekplus.webapp.tool.job.service.JobSearchService;
import com.geekplus.webapp.tool.ppt.dto.PptGenerateRequest;
import com.geekplus.webapp.tool.ppt.service.PptAiService;
import com.geekplus.webapp.tool.resume.dto.ResumeAnalyzeRequest;
import com.geekplus.webapp.tool.resume.service.ResumeAiService;
import org.springframework.web.bind.annotation.*;

/**
 * 简历 AI 能力（与 CRUD 拆分）。
 * PPT 正式入口为 /api/ppt/generate；此处 /generate-ppt 保留兼容。
 */
@RestController
@RequestMapping("/api/resume/ai")
public class ResumeAiController {

    private final AiService aiService;
    private final ResumeAiService resumeAiService;
    private final JobSearchService jobSearchService;
    private final PptAiService pptAiService;

    public ResumeAiController(AiService aiService,
                              ResumeAiService resumeAiService,
                              JobSearchService jobSearchService,
                              PptAiService pptAiService) {
        this.aiService = aiService;
        this.resumeAiService = resumeAiService;
        this.jobSearchService = jobSearchService;
        this.pptAiService = pptAiService;
    }

    @PostMapping("/generate")
    public Result generate(@RequestBody ChatPrompt chatPrompt) {
        try {
            String text = aiService.chat(chatPrompt);
            return Result.success((Object) text);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/generate/v2")
    public Result generateV2(@RequestBody AIRequest request) {
        try {
            return Result.success(aiService.generate(request));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/analyze")
    public Result analyze(@RequestBody ResumeAnalyzeRequest request) {
        try {
            return Result.success(resumeAiService.analyze(request));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Deprecated
    @PostMapping("/job-search")
    public Result jobSearchLegacy(@RequestBody JobSearchRequest request) {
        try {
            return Result.success(jobSearchService.search(request));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * @deprecated 请改用 POST /api/ppt/generate
     */
    @Deprecated
    @PostMapping("/generate-ppt")
    public Result generatePpt(@RequestBody PptGenerateRequest request) {
        try {
            if (request != null && (request.getSourceType() == null || request.getSourceType().isEmpty())) {
                request.setSourceType(request.getResumeData() != null ? "resume" : "text");
            }
            return Result.success(pptAiService.generate(request));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
