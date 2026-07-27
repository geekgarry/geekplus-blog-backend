package com.geekplus.webapp.tool.resume.controller;

import com.geekplus.common.domain.ChatPrompt;
import com.geekplus.common.domain.Result;
import com.geekplus.common.dto.AIRequest;
import com.geekplus.webapp.common.service.AiService;
import com.geekplus.webapp.tool.resume.dto.ResumeSaveRequest;
import com.geekplus.webapp.tool.resume.entity.ResumeData;
import com.geekplus.webapp.tool.resume.service.ResumeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {
    private final ResumeService resumeService;
    private final AiService aiService;

    public ResumeController(ResumeService resumeService, AiService aiService) {
        this.resumeService = resumeService;
        this.aiService = aiService;
    }

    /** 保存：有 id 则更新当前份，无 id 则新建一份；返回保存后的记录（含 id） */
    @PostMapping
    public Result saveResume(@RequestParam(required = false) Long userId, @RequestBody ResumeSaveRequest request) {
        if (userId == null) {
            userId = 0L;
        }
        ResumeData saved = resumeService.saveResume(userId, request);
        return Result.success(saved);
    }

    /** 默认加载用户最新一份 */
    @GetMapping
    public Result loadResume(@RequestParam Long userId) {
        ResumeData resumeData = resumeService.getLatestResume(userId);
        if (resumeData == null) {
            return Result.success(null);
        }
        return Result.success(resumeData);
    }

    /** 用户名下全部简历 */
    @GetMapping("/mine")
    public Result listMine(@RequestParam Long userId) {
        List<ResumeData> list = resumeService.listByUserId(userId);
        return Result.success(list);
    }

    @GetMapping("/list")
    public Result listResumes(@RequestParam(required = false) Long userId) {
        List<ResumeData> resumes = userId != null
                ? resumeService.listByUserId(userId)
                : resumeService.listResumes();
        return Result.success(resumes);
    }

    @GetMapping("/{id}")
    public Result getResumeById(@PathVariable Long id) {
        ResumeData resumeData = resumeService.getResumeById(id);
        if (resumeData == null) {
            return Result.error("简历不存在");
        }
        return Result.success(resumeData);
    }

    @DeleteMapping("/{id}")
    public Result deleteResume(@PathVariable Long id) {
        resumeService.deleteResume(id);
        return Result.success();
    }

    @PostMapping("/ai/generate")
    public Result generate(@RequestBody ChatPrompt chatPrompt) {
        try {
            String text = aiService.chat(chatPrompt);
            return Result.success(text);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/ai/generate/v2")
    public Result generateV2(@RequestBody AIRequest request) {
        try {
            return Result.success(aiService.generate(request));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
