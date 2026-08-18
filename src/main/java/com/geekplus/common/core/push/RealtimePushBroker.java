package com.geekplus.common.core.push;

import com.geekplus.common.core.socket.WebSocketServer;
import com.geekplus.common.core.sse.SsePushHub;
import com.geekplus.common.util.json.JsonObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 双通道推送：同时走 WebSocket + SSE。
 * 推送失败不得向外抛（避免 HTTP 信令接口 500）。
 */
@Slf4j
@Component
public class RealtimePushBroker {

    @Resource
    private SsePushHub ssePushHub;

    public void pushToUser(String clientId, Object message) {
        if (clientId == null || clientId.isEmpty()) {
            return;
        }
        String text = message instanceof String ? (String) message : JsonObjectUtil.objectToJson(message);
        if (text == null || text.isEmpty()) {
            log.warn("pushToUser skip empty payload, clientId={}", clientId);
            return;
        }
        try {
            WebSocketServer.sendInfo(text, clientId);
        } catch (Exception e) {
            log.warn("WS push fail clientId={}: {}", clientId, e.toString());
        }
        try {
            ssePushHub.sendTo(clientId, text);
        } catch (Exception e) {
            log.warn("SSE push fail clientId={}: {}", clientId, e.toString());
        }
    }

    public void pushAll(Object message) {
        String text = message instanceof String ? (String) message : JsonObjectUtil.objectToJson(message);
        if (text == null) {
            return;
        }
        try {
            WebSocketServer.sendMessageAll(text);
        } catch (Exception e) {
            log.warn("WS broadcast fail: {}", e.toString());
        }
        try {
            ssePushHub.sendAll(text);
        } catch (Exception e) {
            log.warn("SSE broadcast fail: {}", e.toString());
        }
    }

    public void pushMapToUser(String clientId, Map<String, Object> map) {
        pushToUser(clientId, map);
    }
}
