package com.geekplus.webapp.tool.resume.controller;

import com.geekplus.common.domain.Result;
import com.geekplus.webapp.tool.resume.dto.ResumeSaveRequest;
import com.geekplus.webapp.tool.resume.entity.ResumeData;
import com.geekplus.webapp.tool.resume.service.ResumeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 简历 CRUD（持久化）。AI 能力见 {@link ResumeAiController}；岗位搜索见 tool.job。
 */
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
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

    /**
     * 仅修改简历名称（不碰正文 JSON）。
     * Body: { "title": "新名称" }
     */
    @PutMapping("/{id}/title")
    public Result renameResume(@PathVariable Long id,
                               @RequestParam(required = false) Long userId,
                               @RequestBody Map<String, String> body) {
        if (userId == null) {
            return Result.error("userId 必填");
        }
        String title = body != null ? body.get("title") : null;
        ResumeData updated = resumeService.renameResume(userId, id, title);
        if (updated == null) {
            return Result.error("简历不存在或无权限");
        }
        return Result.success(updated);
    }
}
