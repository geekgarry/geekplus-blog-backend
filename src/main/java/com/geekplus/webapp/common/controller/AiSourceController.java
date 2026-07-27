package com.geekplus.webapp.common.controller;

import com.geekplus.common.domain.Result;
import com.geekplus.common.dto.GenericAiRequest;
import com.geekplus.webapp.common.entity.AiSource;
import com.geekplus.webapp.common.service.AiService;
import com.geekplus.webapp.common.service.AiSourceService;
import com.geekplus.webapp.common.service.GeminiModelService;
import com.geekplus.webapp.common.service.GenericAiService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 源 + Gemini 官方模型元数据管理 + 通用 AI 源测试
 */
@RestController
@RequestMapping("/system/ai")
public class AiSourceController {

    private final AiSourceService aiSourceService;
    private final AiService aiService;
    private final GeminiModelService geminiModelService;
    private final GenericAiService genericAiService;

    public AiSourceController(AiSourceService aiSourceService, AiService aiService,
                              GeminiModelService geminiModelService, GenericAiService genericAiService) {
        this.aiSourceService = aiSourceService;
        this.aiService = aiService;
        this.geminiModelService = geminiModelService;
        this.genericAiService = genericAiService;
    }

    // ---------- AI 源 CRUD ----------

    @GetMapping("/source/list")
    public Result list() {
        return Result.success(aiSourceService.listAll());
    }

    @GetMapping("/source/{id}")
    public Result get(@PathVariable Long id) {
        return Result.success(aiSourceService.getById(id));
    }

    @PostMapping("/source")
    public Result save(@RequestBody AiSource source) {
        aiSourceService.save(source);
        return Result.success(source);
    }

    @PutMapping("/source")
    public Result update(@RequestBody AiSource source) {
        if (source.getId() == null) {
            return Result.error("id 不能为空");
        }
        aiSourceService.save(source);
        return Result.success(source);
    }

    @PutMapping("/source/default/{id}")
    public Result setDefault(@PathVariable Long id) {
        aiSourceService.setDefault(id);
        return Result.success();
    }

    @DeleteMapping("/source/{id}")
    public Result delete(@PathVariable Long id) {
        aiSourceService.delete(id);
        return Result.success();
    }

    @GetMapping("/source/providers")
    public Result providers() {
        Map<String, Object> data = new HashMap<>();
        data.put("providers", aiService.availableProviders());
        data.put("freeModels", aiSourceService.recommendedFreeModels());
        return Result.success(data);
    }

    /**
     * AI 源连通性测试：预览 URL+Key 或真实 GET/POST。
     * previewOnly=true 时只返回组装结果，不发外网请求。
     */
    @PostMapping("/source/test")
    public Result testSource(@RequestBody GenericAiRequest request) {
        try {
            if (request != null && Boolean.TRUE.equals(request.getPreviewOnly())) {
                return Result.success(genericAiService.preview(request));
            }
            return Result.success(genericAiService.execute(request));
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "测试失败" : e.getMessage());
        }
    }

    // ---------- Gemini 官方 models API ----------

    @GetMapping("/gemini/models")
    public Result listGeminiModels(
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer pageSize) {
        try {
            return Result.success(geminiModelService.listModels(sourceId, method, keyword, pageSize));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/gemini/models/{modelId}")
    public Result getGeminiModel(
            @PathVariable String modelId,
            @RequestParam(required = false) Long sourceId) {
        try {
            return Result.success(geminiModelService.getModel(sourceId, modelId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/gemini/probe")
    public Result probe(@RequestParam(required = false) Long sourceId) {
        return Result.success(geminiModelService.probeKey(sourceId));
    }

    @GetMapping("/gemini/capabilities")
    public Result capabilities() {
        return Result.success(geminiModelService.capabilities());
    }
}
