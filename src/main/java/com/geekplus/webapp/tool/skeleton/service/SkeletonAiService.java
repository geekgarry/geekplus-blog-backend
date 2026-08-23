package com.geekplus.webapp.tool.skeleton.service;

import com.geekplus.common.domain.ChatPrompt;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.webapp.common.service.AiService;
import com.geekplus.webapp.tool.skeleton.dto.SkeletonImageRequest;
import org.springframework.stereotype.Service;

/**
 * 骨架识别 P3：图片 → 约束 Prompt → 本仓库 Skeleton Schema JSON 文本。
 */
@Service
public class SkeletonAiService {

    private static final String SYSTEM_PROMPT =
            "你是前端骨架屏结构助手。根据用户上传的界面截图/设计图，推断加载态占位结构，"
                    + "只输出一个 JSON 对象（不要 markdown 围栏、不要解释），格式严格为：\n"
                    + "{\"version\":2,\"name\":\"图片识别\",\"canvas\":{\"minWidth\":75,\"width\":\"100%\",\"padding\":16,\"gap\":12},"
                    + "\"nodes\":[...]}\n"
                    + "nodes 中每个节点必须有 type，可选属性与下列一致：\n"
                    + "布局：row{gap,align,justify,width,children}；stack{gap,flex,width,children}\n"
                    + "叶子：circle{size}；line{width,height,radius}；lines{width,height,count,gap,lastWidth}；"
                    + "block{width,height,radius}；field{width,height,radius}；textarea{width,height,radius}；gap{height}\n"
                    + "field/textarea 表示输入框外形占位，不是真实表单。优先粗结构，节点总数不超过 60。";

    private final AiService aiService;

    public SkeletonAiService(AiService aiService) {
        this.aiService = aiService;
    }

    public String fromImage(SkeletonImageRequest request) {
        if (request == null || StringUtils.isEmpty(request.getMediaData())) {
            throw new IllegalArgumentException("mediaData 不能为空");
        }
        String mime = StringUtils.isEmpty(request.getMediaMimeType())
                ? "image/jpeg"
                : request.getMediaMimeType();
        String b64 = stripDataUrl(request.getMediaData());

        ChatPrompt prompt = new ChatPrompt();
        prompt.setChatMsg(SYSTEM_PROMPT);
        prompt.setMediaData(b64);
        prompt.setMediaMimeType(mime);
        prompt.setMediaFileName("skeleton-shot.jpg");
        prompt.setProvider(request.getProvider());
        prompt.setModel(request.getModel());
        prompt.setSourceId(request.getSourceId());
        return aiService.chat(prompt);
    }

    private static String stripDataUrl(String raw) {
        String s = raw.trim();
        int idx = s.indexOf("base64,");
        if (idx >= 0) {
            return s.substring(idx + "base64,".length());
        }
        return s;
    }
}
