package com.geekplus.webapp.tool.skeleton.controller;

import com.geekplus.common.domain.Result;
import com.geekplus.webapp.tool.skeleton.dto.SkeletonFetchRequest;
import com.geekplus.webapp.tool.skeleton.dto.SkeletonImageRequest;
import com.geekplus.webapp.tool.skeleton.service.SkeletonAiService;
import com.geekplus.webapp.tool.skeleton.service.SkeletonFetchService;
import org.springframework.web.bind.annotation.*;

/**
 * 骨架屏识别 API：P2 URL 抓取、P3 图片 AI。
 * 前端路径：/tool/skeletonStudio
 */
@RestController
@RequestMapping("/api/skeleton")
public class SkeletonController {

    private final SkeletonFetchService fetchService;
    private final SkeletonAiService aiService;

    public SkeletonController(SkeletonFetchService fetchService, SkeletonAiService aiService) {
        this.fetchService = fetchService;
        this.aiService = aiService;
    }

    /** P2：代理抓取公开页 HTML */
    @PostMapping("/fetch-url")
    public Result fetchUrl(@RequestBody SkeletonFetchRequest request) {
        try {
            return Result.success(fetchService.fetchHtml(request == null ? null : request.getUrl()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /** P3：截图 → Schema JSON 文本 */
    @PostMapping("/from-image")
    public Result fromImage(@RequestBody SkeletonImageRequest request) {
        try {
            return Result.success(aiService.fromImage(request));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
