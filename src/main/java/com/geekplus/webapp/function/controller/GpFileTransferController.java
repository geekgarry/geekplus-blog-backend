package com.geekplus.webapp.function.controller;

import com.geekplus.common.domain.Result;
import com.geekplus.webapp.function.service.IGpFileTransferService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 临时文件中转（免登录，挂在 /geekplusapp 下已 anon）
 */
@Slf4j
@RestController
@RequestMapping("/geekplusapp/transfer")
public class GpFileTransferController {

    @Autowired
    private IGpFileTransferService transferService;

    /**
     * 上传：multipart files + 分享设置
     * Header: X-Fingerprint / X-Machine-Id 用于限流
     */
    @PostMapping("/upload")
    public Result upload(@RequestParam("files") MultipartFile[] files,
                         @RequestParam(value = "expireMinutes", required = false, defaultValue = "60") Integer expireMinutes,
                         @RequestParam(value = "password", required = false) String password,
                         @RequestParam(value = "maxDownloads", required = false, defaultValue = "0") Integer maxDownloads,
                         @RequestParam(value = "burnAfterRead", required = false, defaultValue = "false") Boolean burnAfterRead,
                         @RequestHeader(value = "X-Fingerprint", required = false) String fingerprint,
                         @RequestHeader(value = "X-Machine-Id", required = false) String machineId,
                         HttpServletRequest request) {
        try {
            List<Map<String, Object>> list = transferService.upload(
                    files, expireMinutes, password, maxDownloads,
                    Boolean.TRUE.equals(burnAfterRead), fingerprint, machineId, request);
            Map<String, Object> data = new HashMap<>();
            data.put("items", list);
            data.put("count", list.size());
            return Result.success(data);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("transfer upload failed", e);
            return Result.error("上传失败，请稍后重试");
        }
    }

    /** 公开元信息（不含密码哈希） */
    @GetMapping("/info/{shareCode}")
    public Result info(@PathVariable String shareCode) {
        try {
            return Result.success(transferService.getPublicInfo(shareCode));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 下载：支持 query password 或 header X-Transfer-Password
     * 使用 POST 避免密码进日志（也兼容 GET）
     */
    @RequestMapping(value = "/download/{shareCode}", method = {RequestMethod.GET, RequestMethod.POST})
    public void download(@PathVariable String shareCode,
                         @RequestParam(value = "password", required = false) String password,
                         @RequestHeader(value = "X-Transfer-Password", required = false) String headerPassword,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        try {
            String pwd = password != null ? password : headerPassword;
            transferService.download(shareCode, pwd, request, response);
        } catch (IllegalArgumentException e) {
            writeJsonError(response, 400, e.getMessage());
        } catch (Exception e) {
            log.error("transfer download failed", e);
            writeJsonError(response, 500, "下载失败");
        }
    }

    /** 撤销（需同一指纹或机器号） */
    @PostMapping("/revoke/{shareCode}")
    public Result revoke(@PathVariable String shareCode,
                         @RequestHeader(value = "X-Fingerprint", required = false) String fingerprint,
                         @RequestHeader(value = "X-Machine-Id", required = false) String machineId) {
        boolean ok = transferService.revoke(shareCode, fingerprint, machineId);
        return ok ? Result.success("已撤销") : Result.error("无权撤销或链接不存在");
    }

    private void writeJsonError(HttpServletResponse response, int status, String msg) {
        try {
            response.reset();
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + status + ",\"msg\":\"" + msg.replace("\"", "'") + "\"}");
        } catch (Exception ignore) {
        }
    }
}
