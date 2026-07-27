package com.geekplus.webapp.tool.resume.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekplus.webapp.tool.resume.dto.ResumeSaveRequest;
import com.geekplus.webapp.tool.resume.entity.ResumeData;
import com.geekplus.webapp.tool.resume.mapper.ResumeDataMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class ResumeService {
    private static final String TEMPLATE_FIELD = "__templateId";

    private final ResumeDataMapper resumeDataMapper;
    private final ObjectMapper objectMapper;

    public ResumeService(ResumeDataMapper resumeDataMapper, ObjectMapper objectMapper) {
        this.resumeDataMapper = resumeDataMapper;
        this.objectMapper = objectMapper;
    }

    public ResumeData getLatestResume(Long userId) {
        return enrich(resumeDataMapper.findLatestByUserId(userId));
    }

    public ResumeData getResumeById(Long id) {
        return enrich(resumeDataMapper.findById(id));
    }

    public List<ResumeData> listResumes() {
        List<ResumeData> list = resumeDataMapper.findAll();
        if (list != null) {
            list.forEach(this::enrich);
        }
        return list;
    }

    public List<ResumeData> listByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<ResumeData> list = resumeDataMapper.findListByUserId(userId);
        if (list != null) {
            list.forEach(this::enrich);
        }
        return list;
    }

    public void deleteResume(Long id) {
        resumeDataMapper.deleteById(id);
    }

    /**
     * 多份简历：
     * - 请求带 id 且属于该用户 → 更新该份
     * - 否则 → 新增一份
     */
    public ResumeData saveResume(Long userId, ResumeSaveRequest request) {
        try {
            String dataJson = buildDataJson(request);
            Date now = new Date();
            String title = request.getTitle();
            if (title == null || title.trim().isEmpty()) {
                title = "未命名简历";
            }

            if (request.getId() != null) {
                ResumeData existing = resumeDataMapper.findById(request.getId());
                if (existing != null && userId.equals(existing.getUserId())) {
                    existing.setTitle(title);
                    existing.setDataJson(dataJson);
                    existing.setUpdatedAt(now);
                    resumeDataMapper.update(existing);
                    return enrich(existing);
                }
            }

            ResumeData resumeData = new ResumeData();
            resumeData.setUserId(userId);
            resumeData.setTitle(title);
            resumeData.setDataJson(dataJson);
            resumeData.setUpdatedAt(now);
            resumeDataMapper.insert(resumeData);
            return enrich(resumeData);
        } catch (Exception e) {
            throw new RuntimeException("简历数据序列化失败", e);
        }
    }

    private String buildDataJson(ResumeSaveRequest request) throws Exception {
        JSONObject json;
        if (request.getData() == null) {
            json = new JSONObject();
        } else if (request.getData() instanceof JSONObject) {
            json = (JSONObject) request.getData();
        } else {
            json = JSON.parseObject(objectMapper.writeValueAsString(request.getData()));
        }
        if (request.getTemplateId() != null && !request.getTemplateId().trim().isEmpty()) {
            json.put(TEMPLATE_FIELD, request.getTemplateId());
        }
        return json.toJSONString();
    }

    /** 从 dataJson 中抽出 __templateId 填到 templateKey，便于前端列表展示 */
    private ResumeData enrich(ResumeData data) {
        if (data == null || data.getDataJson() == null) {
            return data;
        }
        try {
            JSONObject json = JSON.parseObject(data.getDataJson());
            if (json != null && json.containsKey(TEMPLATE_FIELD)) {
                data.setTemplateKey(json.getString(TEMPLATE_FIELD));
            }
        } catch (Exception ignore) {
            // ignore parse error
        }
        return data;
    }
}
