package com.geekplus.webapp.common.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekplus.common.core.push.RealtimePushBroker;
import com.geekplus.common.core.socket.WebSocketServer;
import com.geekplus.common.core.sse.SsePushHub;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.domain.Result;
import com.geekplus.webapp.system.entity.SysUser;
import com.geekplus.webapp.system.service.SysUserService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 在线聊天 + WebRTC 信令。
 * 文本/图片/视频消息经 HTTP 写入后由 Broker 推送；WebRTC 媒体 P2P，信令走本接口。
 */
@Slf4j
@RestController
@RequestMapping("/realtime")
public class RealtimeChatController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 聊天图片上限 */
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    /** 聊天短视频上限（更大文件请走文件中转） */
    public static final long MAX_VIDEO_BYTES = 20L * 1024 * 1024;

    @Resource
    private RealtimePushBroker pushBroker;

    @Resource
    private SysUserService sysUserService;

    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<Map<String, Object>>> HISTORY = new ConcurrentHashMap<>();

    @GetMapping("/onlineUsers")
    public Result onlineUsers() {
        Set<String> rawIds = new LinkedHashSet<>();
        rawIds.addAll(WebSocketServer.getWebSocketPool().keySet());
        rawIds.addAll(SsePushHub.onlineClientIds());

        Map<String, Map<String, Object>> byUid = new LinkedHashMap<>();
        for (String raw : rawIds) {
            ResolvedUser ru = resolveUser(raw);
            if (ru == null || ru.clientId == null) {
                continue;
            }
            if (!byUid.containsKey(ru.clientId)) {
                Map<String, Object> row = new HashMap<>();
                row.put("clientId", ru.clientId);
                row.put("userId", ru.userId);
                row.put("userName", ru.userName);
                row.put("nickName", ru.nickName);
                row.put("avatar", ru.avatar);
                byUid.put(ru.clientId, row);
            }
        }
        return Result.success(new ArrayList<>(byUid.values()));
    }

    @PostMapping("/chat/send")
    public Result sendChat(@RequestBody ChatSendReq req) {
        LoginUser me = currentUser();
        if (me == null || req == null || req.getToClientId() == null) {
            return Result.error("参数错误或未登录");
        }
        String fromId = String.valueOf(me.getUserId());
        String toId = normalizeClientId(req.getToClientId());
        if (toId == null || toId.isEmpty()) {
            return Result.error("对方标识无效");
        }

        String msgType = req.getMsgType() == null || req.getMsgType().isEmpty() ? "text" : req.getMsgType().trim().toLowerCase();
        if (!Arrays.asList("text", "image", "video").contains(msgType)) {
            return Result.error("不支持的消息类型");
        }

        String content = req.getContent() == null ? "" : req.getContent().trim();
        String mediaUrl = req.getMediaUrl() == null ? null : req.getMediaUrl().trim();

        if ("text".equals(msgType)) {
            if (content.isEmpty()) {
                return Result.error("消息不能为空");
            }
        } else {
            if (mediaUrl == null || mediaUrl.isEmpty()) {
                return Result.error("媒体地址不能为空");
            }
            if (req.getMediaSize() != null) {
                long max = "image".equals(msgType) ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
                if (req.getMediaSize() > max) {
                    return Result.error("image".equals(msgType)
                            ? "图片不能超过 5MB，请压缩后发送"
                            : "视频不能超过 20MB，较大文件请使用「文件中转」");
                }
            }
            if (content.isEmpty()) {
                content = "image".equals(msgType) ? "[图片]" : "[视频]";
            }
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "chat");
        msg.put("msgType", msgType);
        msg.put("fromClientId", fromId);
        msg.put("toClientId", toId);
        msg.put("fromName", me.getNickname() != null ? me.getNickname() : me.getUsername());
        msg.put("fromAvatar", me.getAvatar());
        msg.put("content", content);
        msg.put("mediaUrl", mediaUrl);
        msg.put("mediaName", req.getMediaName());
        msg.put("mediaSize", req.getMediaSize());
        msg.put("ts", System.currentTimeMillis());

        String key = peerKey(fromId, toId);
        HISTORY.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(msg);
        CopyOnWriteArrayList<Map<String, Object>> hist = HISTORY.get(key);
        while (hist.size() > 200) {
            hist.remove(0);
        }

        pushBroker.pushToUser(toId, msg);
        pushBroker.pushToUser(fromId, msg);
        if (!toId.equals(req.getToClientId())) {
            pushBroker.pushToUser(req.getToClientId(), msg);
        }
        return Result.success(msg);
    }

    @GetMapping("/chat/history")
    public Result history(@RequestParam String peerClientId) {
        LoginUser me = currentUser();
        if (me == null) {
            return Result.error("未登录");
        }
        String fromId = String.valueOf(me.getUserId());
        String peerId = normalizeClientId(peerClientId);
        List<Map<String, Object>> list = HISTORY.getOrDefault(peerKey(fromId, peerId), new CopyOnWriteArrayList<>());
        return Result.success(list);
    }

    /**
     * WebRTC 信令：invite | offer | answer | ice | hangup | reject | error
     * 推送失败不抛 500；ICE/SDP 做安全序列化。
     */
    @PostMapping("/webrtc/signal")
    public Result signal(@RequestBody SignalReq req) {
        try {
            LoginUser me = currentUser();
            if (me == null || req == null || req.getToClientId() == null) {
                return Result.error("参数错误或未登录");
            }
            String signalType = req.getSignalType();
            if (signalType == null || signalType.isEmpty()) {
                return Result.error("缺少 signalType");
            }
            String fromId = String.valueOf(me.getUserId());
            String toId = normalizeClientId(req.getToClientId());
            if (toId == null || toId.isEmpty()) {
                return Result.error("对方标识无效");
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "webrtc");
            payload.put("signalType", signalType);
            payload.put("fromClientId", fromId);
            payload.put("toClientId", toId);
            payload.put("fromName", me.getNickname() != null ? me.getNickname() : me.getUsername());
            payload.put("sdp", toPlainJson(req.getSdp()));
            payload.put("candidate", toPlainJson(req.getCandidate()));
            payload.put("reason", req.getReason());
            // audio | video
            payload.put("callMode", req.getCallMode() == null || req.getCallMode().isEmpty() ? "video" : req.getCallMode());
            payload.put("ts", System.currentTimeMillis());

            pushBroker.pushToUser(toId, payload);
            // hangup/reject/error 也回推给自己，保证本端 UI 能统一收尾（多端/竞态）
            if (Arrays.asList("hangup", "reject", "error").contains(signalType)) {
                pushBroker.pushToUser(fromId, payload);
            }
            if (!toId.equals(req.getToClientId())) {
                pushBroker.pushToUser(req.getToClientId(), payload);
            }
            return Result.success("ok");
        } catch (Exception e) {
            log.warn("webrtc signal failed: {}", e.toString());
            return Result.error("信令发送失败，请重试");
        }
    }

    /** 把前端 RTC 对象转成可 JSON 的纯 Map/List/标量，避免序列化异常 */
    @SuppressWarnings("unchecked")
    private static Object toPlainJson(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return MAPPER.convertValue(raw, Object.class);
        } catch (Exception e) {
            try {
                if (raw instanceof Map) {
                    return new HashMap<>((Map<?, ?>) raw);
                }
            } catch (Exception ignore) {
            }
            return String.valueOf(raw);
        }
    }

    private LoginUser currentUser() {
        try {
            Object p = SecurityUtils.getSubject().getPrincipal();
            if (p instanceof LoginUser) {
                return (LoginUser) p;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static String normalizeClientId(String raw) {
        if (raw == null) {
            return null;
        }
        String id = raw.trim();
        if (id.isEmpty()) {
            return id;
        }
        if (id.contains(":")) {
            String[] parts = id.split(":");
            id = parts[parts.length - 1];
        }
        return id;
    }

    private ResolvedUser resolveUser(String rawId) {
        String key = normalizeClientId(rawId);
        if (key == null || key.isEmpty()) {
            return null;
        }
        ResolvedUser ru = new ResolvedUser();
        try {
            Long uid = Long.parseLong(key);
            SysUser u = sysUserService.selectSysUserById(uid);
            if (u != null) {
                ru.clientId = String.valueOf(u.getUserId());
                ru.userId = u.getUserId();
                ru.userName = u.getUsername();
                ru.nickName = u.getNickname() != null ? u.getNickname() : u.getUsername();
                ru.avatar = u.getAvatar();
                return ru;
            }
        } catch (NumberFormatException ignore) {
            SysUser u = sysUserService.sysUserLoginBy(key);
            if (u != null) {
                ru.clientId = String.valueOf(u.getUserId());
                ru.userId = u.getUserId();
                ru.userName = u.getUsername();
                ru.nickName = u.getNickname() != null ? u.getNickname() : u.getUsername();
                ru.avatar = u.getAvatar();
                return ru;
            }
        } catch (Exception e) {
            log.debug("resolveUser fail: {}", rawId);
        }
        ru.clientId = key;
        ru.userName = key;
        ru.nickName = key;
        return ru;
    }

    private static String peerKey(String a, String b) {
        if (a.compareTo(b) <= 0) {
            return a + "|" + b;
        }
        return b + "|" + a;
    }

    private static class ResolvedUser {
        String clientId;
        Long userId;
        String userName;
        String nickName;
        String avatar;
    }

    @Data
    public static class ChatSendReq {
        private String toClientId;
        /** text | image | video */
        private String msgType;
        private String content;
        private String mediaUrl;
        private String mediaName;
        private Long mediaSize;
    }

    @Data
    public static class SignalReq {
        private String toClientId;
        /** invite | offer | answer | ice | hangup | reject | error */
        private String signalType;
        private Object sdp;
        private Object candidate;
        private String reason;
        /** audio | video */
        private String callMode;
    }
}
