package com.geekplus.common.core.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 推送连接池（与 WebSocketServer 并存，互不替换）。
 * 适用场景：预警/通知等单向服务端推送，比 WebSocket 更轻。
 * 后期高并发可迁到 Netty + Redis Pub/Sub。
 */
@Slf4j
@Component
public class SsePushHub {

    /** 单连接最长存活：2 小时（前端应周期性重连） */
    private static final long DEFAULT_TIMEOUT = 2L * 60 * 60 * 1000;

    /** clientId -> emitters（同用户多端） */
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> CLIENTS = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String clientId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        CLIENTS.computeIfAbsent(clientId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(clientId, emitter));
        emitter.onTimeout(() -> remove(clientId, emitter));
        emitter.onError(e -> remove(clientId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"type\":\"online\",\"userId\":\"" + clientId + "\"}", MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            remove(clientId, emitter);
        }
        log.info("SSE 订阅: {}, 当前客户端数={}", clientId, CLIENTS.size());
        return emitter;
    }

    public void remove(String clientId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = CLIENTS.get(clientId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            CLIENTS.remove(clientId, list);
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    public static Set<String> onlineClientIds() {
        return CLIENTS.keySet();
    }

    public static int onlineCount() {
        return CLIENTS.size();
    }

    /** 推给指定客户端（JSON 字符串） */
    public void sendTo(String clientId, Object payload) {
        CopyOnWriteArrayList<SseEmitter> list = CLIENTS.get(clientId);
        if (list == null || list.isEmpty()) {
            return;
        }
        // 已是 JSON 字符串时用 TEXT_PLAIN，避免 APPLICATION_JSON 二次转义导致前端解析失败
        MediaType mediaType = payload instanceof String ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(payload, mediaType));
            } catch (Exception e) {
                remove(clientId, emitter);
            }
        }
    }

    /** 广播给所有 SSE 客户端 */
    public void sendAll(Object payload) {
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : CLIENTS.entrySet()) {
            sendTo(entry.getKey(), payload);
        }
    }
}
