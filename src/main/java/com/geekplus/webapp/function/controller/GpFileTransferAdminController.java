package com.geekplus.webapp.function.controller;

import com.geekplus.common.domain.Result;
import com.geekplus.webapp.function.entity.GpFileTransfer;
import com.geekplus.webapp.function.service.IGpFileTransferService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 临时文件中转 — 后台管理（需登录）
 */
@RestController
@RequestMapping("/geekplus/fileTransfer")
public class GpFileTransferAdminController {

    @Autowired
    private IGpFileTransferService transferService;

    @GetMapping("/list")
    public Result list(@RequestParam(value = "status", required = false) Integer status,
                       @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit) {
        List<GpFileTransfer> rows = transferService.listForAdmin(status, limit == null ? 50 : limit);
        for (GpFileTransfer row : rows) {
            row.setPasswordHash(null);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("rows", rows);
        data.put("total", rows.size());
        return Result.success(data);
    }

    @PostMapping("/cleanup")
    public Result cleanupNow() {
        int n = transferService.cleanupExpired();
        return Result.success("已清理 " + n + " 项");
    }

    @DeleteMapping("/{id}")
    public Result forceDelete(@PathVariable Long id) {
        boolean ok = transferService.forceDelete(id);
        return ok ? Result.success("已删除") : Result.error("记录不存在");
    }
}
