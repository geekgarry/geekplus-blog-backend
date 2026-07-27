package com.geekplus.webapp.tool.resume.controller;

import com.geekplus.common.domain.Result;
import com.geekplus.webapp.tool.resume.entity.ResumeTemplate;
import com.geekplus.webapp.tool.resume.service.TemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resume/templates")
public class TemplateController {
    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public Result list() {
        return Result.success(templateService.listTemplates());
    }

    @PostMapping
    public Result save(@RequestBody ResumeTemplate template) {
        templateService.saveTemplate(template);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return Result.success();
    }
}
