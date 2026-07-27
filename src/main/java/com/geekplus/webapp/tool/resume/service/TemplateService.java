package com.geekplus.webapp.tool.resume.service;

import com.geekplus.webapp.tool.resume.entity.ResumeTemplate;
import com.geekplus.webapp.tool.resume.mapper.ResumeTemplateMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemplateService {
    private final ResumeTemplateMapper templateMapper;

    public TemplateService(ResumeTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    @Cacheable(value = "resumeTemplates")
    public List<ResumeTemplate> listTemplates() {
        return templateMapper.findAll();
    }

    @CacheEvict(value = "resumeTemplates", allEntries = true)
    public void saveTemplate(ResumeTemplate template) {
        if (template.getId() != null) {
            templateMapper.update(template);
        } else {
            templateMapper.insert(template);
        }
    }

    @CacheEvict(value = "resumeTemplates", allEntries = true)
    public void deleteTemplate(Long id) {
        templateMapper.delete(id);
    }
}
