package com.geekplus.common.util.google;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.geekplus.common.domain.ChatPrompt;
import com.geekplus.common.util.base64.Base64Util;
import com.geekplus.common.util.file.FileUtils;
import com.geekplus.common.util.http.HttpUtils;
import com.geekplus.common.util.json.JsonEscapeUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.http.entity.ContentType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

/**
 * author     : geekplus
 * date       : 5/26/24 11:27 PM
 * description: Gemini AI
 */
@Slf4j
public class GeminiUtils {
    /** 免费档推荐默认模型（Pro 免费配额常为 0，见 rate-limits） */
    public static final String DEFAULT_MODEL = "gemini-2.5-flash";
    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    /** 官方 REST 免费可用的文本模型（按需在后台切换） */
    public static final String[] FREE_TIER_MODELS = {
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite"
    };

    public static String buildGenerateUrl(String baseUrl, String model) {
        String base = normalizeBaseUrl(baseUrl);
        String m = (model == null || model.trim().isEmpty()) ? DEFAULT_MODEL : model.trim();
        if (m.startsWith("models/")) {
            m = m.substring("models/".length());
        }
        return base + "/models/" + m + ":generateContent";
    }

    public static String buildStreamUrl(String baseUrl, String model) {
        String base = normalizeBaseUrl(baseUrl);
        String m = (model == null || model.trim().isEmpty()) ? DEFAULT_MODEL : model.trim();
        if (m.startsWith("models/")) {
            m = m.substring("models/".length());
        }
        return base + "/models/" + m + ":streamGenerateContent?alt=sse";
    }

    private static String friendlyHttpError(org.springframework.web.client.HttpStatusCodeException e) {
        int code = e.getRawStatusCode();
        String body = e.getResponseBodyAsString();
        String detail = null;
        try {
            JSONObject root = JSONObject.parseObject(body);
            if (root != null && root.getJSONObject("error") != null) {
                detail = root.getJSONObject("error").getString("message");
            }
        } catch (Exception ignore) {
            // ignore parse failure
        }
        if (code == 429) {
            return "Gemini 配额不足或请求过于频繁（建议改用 gemini-2.5-flash / flash-lite）。"
                    + (detail != null ? " " + detail : " 请稍后重试。");
        }
        if (code >= 500) {
            return "Gemini 服务暂时不可用（" + code + "），请稍后重试。";
        }
        return detail != null ? detail : ("Gemini 请求失败：" + code);
    }

    //轻量级API
    //curl \
    //-H 'Content-Type: application/json' \
    //-d '{"contents":[{"parts":[{"text":"Explain how AI works"}]}]}' \
    //-X POST 'https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=YOUR_API_KEY'
    //streamGenerateContent?alt=sse
    String apiUrl = DEFAULT_BASE_URL + "/models/" + DEFAULT_MODEL + ":generateContent?key=YOUR_API_KEY";

    private JSONObject requestData;

    //Google Gemini AI REST安全设置参数
    private static String safetySettings = "\"safetySettings\": [\n" +
            "{\"category\": 10, \"threshold\": 4},\n" +
            "{\"category\": 9, \"threshold\": 4},\n" +
            "{\"category\": 8, \"threshold\": 4},\n" +
            "{\"category\": 7, \"threshold\": 4}\n" +
            "],\n";

    public GeminiUtils(){
    }
    //Google Gemini AI请求
    public static String sendGeminiPost(String url, String chatContent, Map<String, String> headerMap) {
        RestTemplate client = new RestTemplate();
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setAll(headerMap);
        if(url==null||"".equals(url)){
            url = buildGenerateUrl(null, DEFAULT_MODEL) + "?key=";
        }
        String requestJson="{\"text: \""+chatContent+"\"}";//构造RequestBody请求体
        HttpEntity<String> entity = new HttpEntity<>(requestJson, httpHeaders);
        ResponseEntity<String> response = client.exchange(url, HttpMethod.POST, entity, String.class);
        System.out.println(response.getBody());
        return response.getBody();
    }

    //Gemini AI Chat请求方法,构造请求json或参数使用字符串拼接的方式
    public static String postGemini(ChatPrompt chatPrompt, String apiKey) throws IOException {
        return postGemini(chatPrompt, apiKey, DEFAULT_MODEL, null);
    }

    public static String postGemini(ChatPrompt chatPrompt, String apiKey, String model, String baseUrl) throws IOException {
        String geminiReply = null;
        String url = buildGenerateUrl(baseUrl, model);
        RestTemplate client = new RestTemplate();
        HttpHeaders httpHeaders = new HttpHeaders();
        //httpHeaders.setAll(headerMap);
        //httpHeaders.add("Authorization", "Bearer "+apiKey);
        httpHeaders.add("Content-Type", "application/json"); // 传递请求体时必须设置
        httpHeaders.add("x-goog-api-key", apiKey);
        HttpEntity<?> entity;
        //这里没有采用Map来构造消息请求体，而是直接拼接字符串
        //Google Gemini AI REST安全设置参数
        StringBuffer requestJson = new StringBuffer("{" + safetySettings);
        // 判断消息提示中是否带有媒体文件的数据内容，为空测没有，表示是一次普通的文本消息请求，
        // 否则就是带有文件数据，具体再分析数据类型
        if(ObjectUtils.isEmpty(chatPrompt.getMediaData())){
            requestJson.append("\"contents\":[\n" +
                    "{\"parts\":[" +
                    "{\"text\":\"" + JsonEscapeUtil.replaceEscapeString(chatPrompt.getChatMsg()) + "\"}\n" +
                    "]}\n" +
                    "]}");
            entity = new HttpEntity<String>(requestJson.toString(), httpHeaders);
        }else{
            String mimeType = chatPrompt.getMediaMimeType();//Base64Util.getFileMimeType(chatPrompt.getMediaData().toString())
            //Base64Util.isBase64(mediaData.toString()) && Base64Util.isImageFromBase64(mediaData.toString())
            //判断是否是字符串形式的数据类型，因为前端发送的是base64字符串编码后的文件
            if(chatPrompt.getMediaData() instanceof String || FileUtils.isStringType(chatPrompt.getMediaData())) {
                requestJson.append("\"contents\":[\n" +
                        "{\"parts\":[\n" +
                        "{\"text\":\"" + JsonEscapeUtil.replaceEscapeString(chatPrompt.getChatMsg()) + "\"},\n" +
                        "{\"inline_data\":\n" +
                        "{\"mime_type\": \"" + mimeType + "\",\n" +
                        "\"data\": \"" + Base64Util.getBase64Str(chatPrompt.getMediaData().toString()) + "\"\n" +
                        "}}\n" +
                        "]}\n" +
                        "]}");
//                requestJson = String.format("{" + safetySettings +
//                "\"contents\":[" +
//                "{\"parts\":["+
//                "{\"text\": \"%s\"}, %n" +
//                "{\"inline_data\":{" +
//                "\"mime_type\": \""+mimeType+"\", %n" +
//                "\"data\": \""+Base64Util.getBase64Str(chatPrompt.getMediaData().toString())+"\" %n" +
//                "}}" +
//                "]}" +
//                "]}", JsonEscapeUtil.replaceEscapeString(chatPrompt.getChatMsg()));
                entity = new HttpEntity<String>(requestJson.toString(), httpHeaders);
            }else{
                //byte[] fileToByte = (byte[]) chatPrompt.getMediaData();
                //ByteArrayResource resource = new ByteArrayResource(fileToByte){
                //@Override
                //public String getFilename() {
                //return fileName;
                //}
                //};
                httpHeaders.set("Content-Type","multipart/form-data");// 传递带有文件的请求
//                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//                ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
//                objectOutputStream.writeObject(chatPrompt.getMediaData());
//                objectOutputStream.flush();
//                byte[] byteStream = byteArrayOutputStream.toByteArray();
                // 数组转输入流
                InputStream inputStream = new ByteArrayInputStream((byte[]) chatPrompt.getMediaData());
                // 输入流转MultipartFile对象
                MultipartFile multipartFile = new MockMultipartFile(ContentType.APPLICATION_OCTET_STREAM.toString(), inputStream);
                // 把MultipartFile这个对象转成输入流资源(InputStreamResource)
                InputStreamResource isr = new InputStreamResource(multipartFile.getInputStream(), chatPrompt.getMediaFileName());
                requestJson.append("\"contents\":[\n" +
                        "{\"parts\":[\n" +
                        "{\"text\":\"" + JsonEscapeUtil.replaceEscapeString(chatPrompt.getChatMsg()) + "\"},\n" +
                        "{\"inline_data\":\n" +
                        "{\"mime_type\": \"" + mimeType + "\",\n" +
                        "\"data\": \"" + isr + "\"\n" +
                        "}}\n" +
                        "]}\n" +
                        "]}");
                MultiValueMap<String, Object> formChatPromptMap = new LinkedMultiValueMap<>();
                formChatPromptMap.setAll(createSafetySettingsMap());
                Map<String,Object> msgByteDataMap = createMsgPromptMap("user", chatPrompt.getChatMsg(), mimeType, isr);
                formChatPromptMap.put("contents", Collections.singletonList(msgByteDataMap));
                entity = new HttpEntity<MultiValueMap<String,Object>>(formChatPromptMap, httpHeaders);
            }
        }
        try {
            ResponseEntity<String> response = client.exchange(url, HttpMethod.POST, entity, String.class);
            String candidatesResponse = response.getBody();
            log.info("Gemini model={} 响应数据 {}", model, candidatesResponse);
            JSONObject jsonObject = JSONObject.parseObject(candidatesResponse);
            if (!CollectionUtils.isEmpty(jsonObject.getJSONArray("candidates"))) {
                JSONArray candidates = jsonObject.getJSONArray("candidates");
                if (candidates.getJSONObject(0).containsKey("content")) {
                    JSONObject candidatesContent = candidates.getJSONObject(0).getJSONObject("content");
                    JSONArray contentParts = candidatesContent.getJSONArray("parts");
                    geminiReply = contentParts.getJSONObject(0).getString("text");
                } else {
                    geminiReply = "抱歉，我可能出了点问题，请稍后再试！";
                }
            } else {
                log.info("错误消息：{}", jsonObject);
                JSONObject errorData = jsonObject.getJSONObject("error");
                geminiReply = errorData != null && errorData.get("message") != null
                        ? errorData.get("message").toString()
                        : "Gemini 返回空结果";
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Gemini HTTP 错误 model={} status={}", model, e.getRawStatusCode(), e);
            geminiReply = friendlyHttpError(e);
        }
        return geminiReply;
    }

    public static Object postGenerateImage(String parameter, String apiKey) {
        //"https://generativelanguage.googleapis.com/v1beta/models/imagen-4.0-generate-001:predict"
        return null;
    }

    public static Object getAllGeminiModels(String apiKey) throws Exception {
        return listModels(apiKey, null, 1000, null);
    }

    /** 规范化 Gemini API baseUrl，避免误配成 generateContent 完整路径 */
    public static String normalizeBaseUrl(String baseUrl) {
        String base = (baseUrl == null || baseUrl.trim().isEmpty())
                ? DEFAULT_BASE_URL
                : baseUrl.trim().replaceAll("/+$", "");
        // 误配：.../v1beta/models/xxx:generateContent → 截到 .../v1beta
        int modelsIdx = base.indexOf("/models/");
        if (modelsIdx > 0) {
            base = base.substring(0, modelsIdx);
        }
        // 误配：.../v1beta/models → 去掉末尾 /models
        if (base.endsWith("/models")) {
            base = base.substring(0, base.length() - "/models".length());
        }
        return base;
    }

    /**
     * GET https://generativelanguage.googleapis.com/v1beta/models?key=$GEMINI_API_KEY
     * 官方 Shell 示例：key 放在 URL 查询参数中。
     * @see <a href="https://ai.google.dev/api/models">models.list</a>
     */
    public static String listModels(String apiKey, String baseUrl, Integer pageSize, String pageToken) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Gemini API Key 为空");
        }
        String base = normalizeBaseUrl(baseUrl);
        // 与文档一致：.../v1beta/models?key=...
        StringBuilder url = new StringBuilder(base)
                .append("/models?key=")
                .append(URLEncoder.encode(apiKey.trim(), "UTF-8"));
        if (pageSize != null && pageSize > 0) {
            url.append("&pageSize=").append(Math.min(pageSize, 1000));
        }
        if (pageToken != null && !pageToken.isEmpty()) {
            url.append("&pageToken=").append(URLEncoder.encode(pageToken, "UTF-8"));
        }
        return geminiGetByUrl(url.toString());
    }

    /**
     * GET https://generativelanguage.googleapis.com/v1beta/models/{model}?key=$GEMINI_API_KEY
     * @see <a href="https://ai.google.dev/api/models">models.get</a>
     */
    public static String getModel(String apiKey, String baseUrl, String modelName) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Gemini API Key 为空");
        }
        String base = normalizeBaseUrl(baseUrl);
        String name = modelName == null ? "" : modelName.trim();
        if (name.startsWith("models/")) {
            name = name.substring("models/".length());
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("modelName 不能为空");
        }
        String url = base + "/models/" + name + "?key=" + URLEncoder.encode(apiKey.trim(), "UTF-8");
        return geminiGetByUrl(url);
    }

    /** GET 已含 key 查询参数的完整 URL（与浏览器直接访问一致） */
    private static String geminiGetByUrl(String url) throws Exception {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            // 重要：GET 不要 setDoOutput(true)，否则部分环境会异常/空响应
            conn.setDoOutput(false);
            conn.setDoInput(true);
            conn.connect();

            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) {
                throw new IllegalStateException("Gemini GET 无响应流，HTTP " + code);
            }
            reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String body = sb.toString();
            if (code >= 400) {
                String safeUrl = url.replaceAll("([?&]key=)[^&]*", "$1***");
                log.error("Gemini GET 失败 url={} status={} body={}", safeUrl, code, body);
                try {
                    JSONObject root = JSONObject.parseObject(body);
                    if (root != null && root.getJSONObject("error") != null) {
                        throw new IllegalStateException(root.getJSONObject("error").getString("message"));
                    }
                } catch (IllegalStateException ex) {
                    throw ex;
                } catch (Exception ignore) {
                    // fallthrough
                }
                throw new IllegalStateException("Gemini 请求失败：" + code);
            }
            return body;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignore) {
                    // ignore
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * POST .../models/{model}:countTokens
     */
    public static String countTokens(String apiKey, String baseUrl, String modelName, String text) throws Exception {
        String base = normalizeBaseUrl(baseUrl);
        String name = modelName == null ? DEFAULT_MODEL : modelName.trim();
        if (name.startsWith("models/")) {
            name = name.substring("models/".length());
        }
        String url = base + "/models/" + name + ":countTokens";
        RestTemplate client = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("x-goog-api-key", apiKey);
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> content = createMsgPromptMap(null, text == null ? "" : text, null, null);
        body.put("contents", Collections.singletonList(content));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = client.exchange(url, HttpMethod.POST, entity, String.class);
            return response.getBody();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw new IllegalStateException(friendlyHttpError(e), e);
        }
    }

    //Gemini AI Chat请求方法
    public static Object postGeminiTTS(String url, String text, String voiceName, String apiKey) throws IOException {
        Object mediaData = null;
        //https://generativelanguage.googleapis.com/v1beta/{model=models/*}:streamGenerateContent
        //url="https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse";
        url="https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent";
        RestTemplate client = new RestTemplate();
        HttpHeaders httpHeaders = new HttpHeaders();
        //httpHeaders.setAll(headerMap);
        //httpHeaders.add("Authorization", "Bearer "+apiKey);
        httpHeaders.add("Content-Type", "application/json"); // 传递请求体时必须设置
        httpHeaders.add("x-goog-api-key", apiKey);
        HttpEntity<?> entity = null;
        //历史消息列表
        List<Map<String,Object>> historyChatDataList = new ArrayList<>();
        //构造了最外层的一个包含所有Json的key:value的包裹Map
        Map<String,Object> finalChatPromptMap = new HashMap<>();
        // 否则就是带有文件数据，具体再分析数据类型
        //为发送的消息构造一个消息内容Map
        Map<String,Object> commonMsgDataMap = createMsgPromptMap(null, text,null,null);
        //因为是聊天模式，所以向历史消息列表添加这个新构造的消息Map
        historyChatDataList.add(commonMsgDataMap);
        //最后把消息主题内容添加到key为contents的Map
        finalChatPromptMap.put("contents",historyChatDataList);
        //首先向包裹填装基本设置Map
        finalChatPromptMap.put("generationConfig", generationConfigMap(voiceName));//put("safetySettings",null);
        finalChatPromptMap.put("model", "gemini-2.5-flash-preview-tts");
        //最后把构造的Map消息放入entity请求体，这里就相当于前端的json放入RequestBody
        entity = new HttpEntity<>(finalChatPromptMap, httpHeaders);

        ResponseEntity<String> response = client.exchange(url, HttpMethod.POST, entity, String.class);
        String candidatesPart=response.getBody();
        log.info("响应数据 {}",response.getBody());
        JSONObject jsonObject = JSONObject.parseObject(candidatesPart);
        if (!CollectionUtils.isEmpty(jsonObject.getJSONArray("candidates"))) {
            JSONArray candidates = jsonObject.getJSONArray("candidates");
            if (candidates.getJSONObject(0).containsKey("content")) {
                JSONObject candidatesContent = candidates.getJSONObject(0).getJSONObject("content");
                JSONArray contentParts = candidatesContent.getJSONArray("parts");
                String contentRole = candidatesContent.getString("role");
                mediaData = contentParts.getJSONObject(0).get("inlineData");
                //String messagepre = messageResponseBody.getChoices().get(0).getText();
                //AIReplyText.substring(2);
            }
        }
        return mediaData;
    }

    //Gemini AI Chat请求方法
    public static String postGeminiHistory(ChatPrompt chatPrompt, String apiKey) throws IOException {
        return postGeminiHistory(chatPrompt, apiKey, DEFAULT_MODEL, null);
    }

    public static String postGeminiHistory(ChatPrompt chatPrompt, String apiKey, String model, String baseUrl) throws IOException {
        String geminiReply=null;
        String url = buildGenerateUrl(baseUrl, model);
        RestTemplate client = new RestTemplate();
        HttpHeaders httpHeaders = new HttpHeaders();
        //httpHeaders.setAll(headerMap);
        //httpHeaders.add("Authorization", "Bearer "+apiKey);
        httpHeaders.add("Content-Type", "application/json"); // 传递请求体时必须设置
        httpHeaders.add("x-goog-api-key", apiKey);
        HttpEntity<?> entity;
//        String requestJson1 = String.format(
//                "{\"contents\":[" +
//                        "{\"role\":\"user\",\n" +
//                        "\"parts\":[{\n" +
//                        "\"text\": \"%s\"}]" +
//                        "}, %n" +
//                        "{\"role\":\"model\",\n" +
//                        "\"parts\":[{\n" +
//                        "\"text\": \"%s\"}]" +
//                        "}, %n" +
//                "]}", chatContent,chatContent
//        );
        //json字符串拼接
        String requestJson ="{" + safetySettings +
                "\"contents\":[\n" +
                chatPrompt.getPreChatData() + "\n"+
                "{\"role\":\"user\",\n" +
                "\"parts\":[{\n" +
                "\"text\": \""+chatPrompt.getChatMsg()+"\"}]}\n" +
                "]}";
        //历史消息列表
        List<Map<String,Object>> historyChatDataList = chatPrompt.getHistoryChatData();
        //构造了最外层的一个包含所有Json的key:value的包裹Map
        Map<String,Object> finalChatPromptMap = new HashMap<>();
        //首先向包裹填装基本不变的安全设置Map
        finalChatPromptMap.putAll(createSafetySettingsMap());//put("safetySettings",null);
        // 判断消息提示中是否带有媒体文件的数据内容，为空测没有，表示是一次普通的文本消息请求，
        // 否则就是带有文件数据，具体再分析数据类型
        if(ObjectUtils.isEmpty(chatPrompt.getMediaData())){
            //为发送的消息构造一个消息内容Map
            Map<String,Object> commonMsgDataMap = createMsgPromptMap("user", chatPrompt.getChatMsg(),null,null);
            //因为是聊天模式，所以向历史消息列表添加这个新构造的消息Map
            historyChatDataList.add(commonMsgDataMap);
            //最后把消息主题内容添加到key为contents的Map
            finalChatPromptMap.put("contents",historyChatDataList);
            //最后把构造的Map消息放入entity请求体，这里就相当于前端的json放入RequestBody
            entity = new HttpEntity<>(finalChatPromptMap, httpHeaders);
        }else{
            String mimeType= chatPrompt.getMediaMimeType();//Base64Util.getFileMimeType(chatPrompt.getMediaData().toString());
            //判断是否是字符串形式的数据类型，因为前端发送的是base64字符串编码后的文件
            if(chatPrompt.getMediaData() instanceof String || FileUtils.isStringType(chatPrompt.getMediaData())) {
                requestJson="{" + safetySettings +
                        "\"contents\":[\n" +
                        chatPrompt.getPreChatData() + "\n"+
                        "{\"role\":\"user\",\n" +
                        "\"parts\":[\n" +
                        "{\"text\": \""+chatPrompt.getChatMsg()+"\"},\n" +
                        "{\"inline_data\":\n" +
                        "{\"mime_type\": \""+mimeType+"\",\n" +
                        "\"data\": \""+Base64Util.getBase64Str(chatPrompt.getMediaData().toString())+"\"\n" +
                        "}}\n]}\n" +
                        "]}";
                //因为发送消息携带base64文件，为发送的消息构造一个消息内容Map，里面再次添加inline_data等所需要的内容，这里重新设置一个新的消息Map，因为这个是携带媒体文件数据的消息
                Map<String,Object> msgMediaDataMap = createMsgPromptMap("user", chatPrompt.getChatMsg(), mimeType, Base64Util.getBase64Str(chatPrompt.getMediaData().toString()));
                //还是一样添加到所有聊天记录list
                historyChatDataList.add(msgMediaDataMap);
                //同样添加到最外的构造消息请求体的Map
                finalChatPromptMap.put("contents", historyChatDataList);
                entity = new HttpEntity<>(finalChatPromptMap, httpHeaders);
            }else{
                httpHeaders.set("Content-Type", "multipart/form-data"); // 传递请求体时必须设置
//                byte[] fileToByte = (byte[]) chatPrompt.getMediaData();
//                ByteArrayResource resource = new ByteArrayResource(fileToByte);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                objectOutputStream.writeObject(chatPrompt.getMediaData());
                objectOutputStream.flush();
                byte[] byteStream = byteArrayOutputStream.toByteArray();
                requestJson="{" + safetySettings +
                        "\"contents\":[\n" +
                        chatPrompt.getPreChatData() + "\n"+
                        "{\"role\":\"user\",\n" +
                        "\"parts\":[\n" +
                        "{\"text\": \""+chatPrompt.getChatMsg()+"\"},\n" +
                        "{\"inline_data\":\n" +
                        "{\"mime_type\": \""+mimeType+"\",\n" +
                        "\"data\": \""+byteStream+"\"\n" +
                        "}}\n]}\n" +
                        "]}";
                //这里与上面一样，就是类型变成MultiValueMap，这是上传大型文件
                MultiValueMap<String, Object> formChatPromptMap = new LinkedMultiValueMap<>();
                formChatPromptMap.setAll(createSafetySettingsMap());
                Map<String,Object> msgByteDataMap = createMsgPromptMap("user", chatPrompt.getChatMsg(), mimeType, byteStream);
                historyChatDataList.add(msgByteDataMap);
                formChatPromptMap.addAll("contents", historyChatDataList);
                //formChatPromptMap.setAll(JSONObject.parseObject(requestJson));
                entity = new HttpEntity<MultiValueMap<String,Object>>(formChatPromptMap, httpHeaders);
            }
        }
        try {
            ResponseEntity<String> response = client.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("Gemini history model={} 响应数据 {}", model, response.getBody());
            JSONObject jsonObject = JSONObject.parseObject(response.getBody());
            if (!CollectionUtils.isEmpty(jsonObject.getJSONArray("candidates"))) {
                JSONArray candidates = jsonObject.getJSONArray("candidates");
                if (candidates.getJSONObject(0).containsKey("content")) {
                    JSONObject candidatesContent = candidates.getJSONObject(0).getJSONObject("content");
                    JSONArray contentParts = candidatesContent.getJSONArray("parts");
                    geminiReply = contentParts.getJSONObject(0).getString("text");
                } else {
                    geminiReply = "抱歉，我可能出了点问题，请稍后再试！";
                }
            } else {
                log.info("错误消息：{}", jsonObject);
                JSONObject errorData = jsonObject.getJSONObject("error");
                geminiReply = errorData != null && errorData.get("message") != null
                        ? errorData.get("message").toString()
                        : "Gemini 返回空结果";
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Gemini history HTTP 错误 model={} status={}", model, e.getRawStatusCode(), e);
            geminiReply = friendlyHttpError(e);
        }
        return geminiReply;
    }

    //创建安全参数设置的Map
    public static Map<String,Object> generationConfigMap(String voiceName){
        Map<String, Object> generationConfig = new HashMap<>();
        Map<String, Object> voiceConfig = new HashMap<>();
        Map<String, Object> voiceNameMap = new HashMap<>();
        Map<String, Object> speechConfig = new HashMap<>();
        voiceNameMap.put("voiceName", voiceName);
        String[] audios= {"AUDIO"};
        voiceConfig.put("prebuiltVoiceConfig", voiceNameMap);
        speechConfig.put("voiceConfig", voiceConfig);
        generationConfig.put("responseModalities", audios);
        generationConfig.put("speechConfig", speechConfig);
        return generationConfig;
    }

    //创建安全参数设置的Map
    public static Map<String,Object> createSafetySettingsMap(){
        Map<String,Object> safetySettingsMap = new HashMap<>();
        List<Map<String,Object>> safetyList=new ArrayList<>();
        Map<String,Object> safetyMap10=new HashMap<>();
        safetyMap10.put("category",10);
        safetyMap10.put("threshold",4);
        safetyList.add(safetyMap10);
        Map<String,Object> safetyMap9=new HashMap<>();
        safetyMap9.put("category",9);
        safetyMap9.put("threshold",4);
        safetyList.add(safetyMap9);
        Map<String,Object> safetyMap8=new HashMap<>();
        safetyMap8.put("category",8);
        safetyMap8.put("threshold",4);
        safetyList.add(safetyMap8);
        Map<String,Object> safetyMap7=new HashMap<>();
        safetyMap7.put("category",7);
        safetyMap7.put("threshold",4);
        safetyList.add(safetyMap7);
        safetySettingsMap.put("safetySettings",safetyList);
//        System.out.println(JSON.toJSONString(promptJson, SerializerFeature.DisableCircularReferenceDetect));
//        System.out.println(new JSONObject(safetySettingsJson).toString());;
        return safetySettingsMap;
    }

    // 创建消息主题内容的Map
    // {\"role\": \"user/model\", \"parts\":[{\"text\": \"消息内容\", \"inline_data\": { \"mime_type\":\"image/png\",\"data\": \"String/byte\" }}]}
    public static Map<String,Object> createMsgPromptMap(String role, String textContent, String mimeType, Object fileData){
        Map<String,Object> msgPromptMap = new HashMap<>();
        //没有role就不添加Map
        if(role !=null && !role.isEmpty()) {
            msgPromptMap.put("role", role);
        }
        //消息数组parts，和role是同样在一个层级
        //属于parts消息数组部分，包括text消息内容文本，以及可选携带媒体文件数据inline_data
        List<Map<String,Object>> partsList = new ArrayList<>();
        // 添加 text 元素
        Map<String,Object> textContentMap = new HashMap<>();
        textContentMap.put("text", textContent);
        //parts数组，里面包括重要的消息text，另外可以选择是否携带文件数据inline_data，里面包括数据类型和数据内容
        partsList.add(textContentMap);
        //表示如果没有文件就添加文件的相关Map
        if(mimeType !=null && !mimeType.isEmpty()) {
            Map<String, Object> inlineDataMap = new HashMap<>();
            Map<String, Object> dataContentMap = new HashMap<>();
            inlineDataMap.put("mime_type", mimeType);
            inlineDataMap.put("data", fileData);
            dataContentMap.put("inline_data", inlineDataMap);
            partsList.add(dataContentMap);
        }
        msgPromptMap.put("parts", partsList);
        return msgPromptMap;
    }

    // 创建文件上传后返回Uri消息主题内容的Map
    // {\"parts\":[{\"text\": \"消息内容\", \"file_data\": { \"mime_type\":\"video/mp4\",\"file_uri\": \"fileUri\" }}]}
    private static Map<String,Object> createMsgFileDataMap(String textContent, String mimeType, String fileUri){
        Map<String,Object> msgFileDatatMap = new HashMap<>();
        //消息数组parts，和role是同样在一个层级
        List<Map<String,Object>> partsList = new ArrayList<>();
        //属于parts消息数组部分，包括text消息内容文本，以及可选携带媒体文件数据inline_data
        // 添加 text 元素
        Map<String,Object> textContentMap = new HashMap<>();
        textContentMap.put("text", textContent);
        partsList.add(textContentMap);
        //表示如果没有文件就添加文件的相关Map
        if(mimeType !=null && !mimeType.isEmpty()) {
            Map<String, Object> inlineDataMap = new HashMap<>();
            Map<String, Object> dataContentMap = new HashMap<>();
            inlineDataMap.put("mime_type", mimeType);
            inlineDataMap.put("file_uri", fileUri);
            dataContentMap.put("file_data", inlineDataMap);
            partsList.add(dataContentMap);
        }
        //parts数组，里面包括重要的消息text，另外可以选择是否携带文件数据inline_data，里面包括数据类型和数据内容
        msgFileDatatMap.put("parts", partsList);
        return msgFileDatatMap;
    }
}
