package com.geekplus.webapp.common.controller;

import com.geekplus.common.core.sse.SsePushHub;
import com.geekplus.common.domain.Result;
import com.geekplus.common.util.json.JsonObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * SSE 预警/通知订阅入口（替代 WebSocket 的轻量方案，WS 代码保留不动）。
 *
 * 连接：GET /sse/subscribe/{clientId}?Plus-Token=xxx
 * （EventSource 无法自定义 Header，故支持 query 传 Token；JwtFilter 已兼容）
 */
@Slf4j
@RestController
@RequestMapping("/sse")
public class SsePushController {

    @Resource
    private SsePushHub ssePushHub;

    @GetMapping(value = "/subscribe/{clientId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String clientId) {
        return ssePushHub.subscribe(clientId);
    }

    /** 服务端主动推送测试 / 业务调用 */
    @PostMapping("/push/{clientId}")
    public Result push(@PathVariable String clientId, @RequestBody(required = false) Map<String, Object> body,
                       @RequestParam(required = false) String message) {
        Object payload = body != null ? body : message;
        if (payload == null) {
            Map<String, Object> m = new HashMap<>();
            m.put("type", "notify");
            m.put("message", "ping");
            payload = m;
        }
        String text = payload instanceof String ? (String) payload : JsonObjectUtil.objectToJson(payload);
        ssePushHub.sendTo(clientId, text);
        return Result.success("ok");
    }

    @GetMapping("/online")
    public Result online() {
        Map<String, Object> data = new HashMap<>();
        data.put("count", SsePushHub.onlineCount());
        data.put("clients", SsePushHub.onlineClientIds());
        return Result.success(data);
    }
}
