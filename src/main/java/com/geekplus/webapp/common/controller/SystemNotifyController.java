package com.geekplus.webapp.common.controller;

import com.geekplus.common.core.push.RealtimePushBroker;
import com.geekplus.common.domain.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统通知推送：同时走 WebSocket + SSE（RealtimePushBroker）。
 */
@RestController
@RequestMapping("/geekplus/socket")
public class SystemNotifyController {

    @Resource
    private RealtimePushBroker realtimePushBroker;

    @GetMapping("/index/{userId}")
    public ModelAndView socket(@PathVariable String userId) {
        ModelAndView mav = new ModelAndView("/socket1");
        mav.addObject("userId", userId);
        return mav;
    }

    @ResponseBody
    @RequestMapping("/socket/push/{cid}")
    public Result pushToWeb(@PathVariable String cid, String message) {
        Map<String, Object> result = new HashMap<>();
        realtimePushBroker.pushToUser(cid, message);
        result.put("code", cid);
        result.put("msg", message);
        return Result.success(result);
    }
}
