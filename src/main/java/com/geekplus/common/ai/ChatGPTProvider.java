package com.geekplus.common.ai;

import com.geekplus.common.domain.ChatPrompt;
import com.geekplus.common.dto.AIRequest;
import com.geekplus.common.util.collections.MapUtils;
import com.geekplus.common.util.string.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * ChatGPT（OpenAI Chat Completions API，与 ChatGPTService 对齐）
 * provider 编码为 chatgpt，与 gemini 并列；请求 URL 仍指向 api.openai.com。
 */
@Slf4j
@Component
public class ChatGPTProvider implements AIProvider {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String providerName() {
        return "chatgpt";
    }

    @Override
    public String generate(AIRequest request, AiRuntimeConfig config) {
        ChatPrompt prompt = new ChatPrompt();
        prompt.setChatMsg(buildUserPrompt(request));
        return chat(prompt, config);
    }

    @Override
    public String chat(ChatPrompt chatPrompt, AiRuntimeConfig config) {
        if (config == null || StringUtils.isEmpty(config.getApiKey())) {
            return "ChatGPT API Key 未配置，请在 YAML(ai.chatgpt.api-key) 或后台 AI 源中配置。";
        }
        String apiUrl = StringUtils.isNotEmpty(config.getApiUrl())
                ? config.getApiUrl()
                : "https://api.openai.com/v1/chat/completions";
        String model = StringUtils.isNotEmpty(config.getModel()) ? config.getModel() : "gpt-4o-mini";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.7);
        requestBody.put("messages", new ArrayList<>(Arrays.asList(
                MapUtils.of("role", "system", "content", "你是一个专业、简洁的中文助手。"),
                MapUtils.of("role", "user", "content", chatPrompt.getChatMsg())
        )));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);
            return extractContent(response.getBody());
        } catch (HttpStatusCodeException e) {
            log.error("ChatGPT HTTP 错误 status={}", e.getRawStatusCode(), e);
            if (e.getRawStatusCode() == 429) {
                return "ChatGPT 配额不足或请求过于频繁，请稍后重试。";
            }
            return "ChatGPT 请求失败：" + e.getRawStatusCode();
        } catch (Exception e) {
            log.error("ChatGPT 调用失败", e);
            return "ChatGPT 调用失败：" + e.getMessage();
        }
    }

    private String buildUserPrompt(AIRequest request) {
        if (StringUtils.isNotEmpty(request.getPrompt())) {
            return request.getPrompt();
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下信息生成或优化简历内容：\n");
        prompt.append("操作类型：").append(request.getAction()).append("\n");
        if (request.getTemplateKey() != null) {
            prompt.append("模板类型：").append(request.getTemplateKey()).append("\n");
        }
        prompt.append("当前简历数据：").append(request.getResumeData()).append("\n");
        return prompt.toString();
    }

    private String extractContent(Map responseBody) {
        if (responseBody == null) {
            return "AI 生成失败，未获取有效响应。";
        }
        Object choices = responseBody.get("choices");
        if (choices instanceof Iterable) {
            for (Object item : (Iterable<?>) choices) {
                if (item instanceof Map) {
                    Object message = ((Map<?, ?>) item).get("message");
                    if (message instanceof Map) {
                        Object content = ((Map<?, ?>) message).get("content");
                        if (content != null) {
                            return content.toString();
                        }
                    }
                }
            }
        }
        return responseBody.toString();
    }
}
