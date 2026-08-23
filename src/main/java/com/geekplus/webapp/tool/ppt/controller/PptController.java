package com.geekplus.webapp.tool.ppt.controller;

import com.geekplus.common.domain.Result;
import com.geekplus.webapp.tool.ppt.dto.PptGenerateRequest;
import com.geekplus.webapp.tool.ppt.service.PptAiService;
import org.springframework.web.bind.annotation.*;

/**
 * 独立 PPT 工具 API（与简历模块解耦）。
 * 兼容：旧路径仍可由 ResumeAiController 转发。
 */
@RestController
@RequestMapping("/api/ppt")
public class PptController {

    private final PptAiService pptAiService;

    public PptController(PptAiService pptAiService) {
        this.pptAiService = pptAiService;
    }

    /**
     * 生成幻灯片 JSON。
     * sourceType=resume|text|file
     */
    @PostMapping("/generate")
    public Result generate(@RequestBody PptGenerateRequest request) {
        try {
            return Result.success(pptAiService.generate(request));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
