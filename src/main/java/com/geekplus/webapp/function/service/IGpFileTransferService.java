package com.geekplus.webapp.function.service;

import com.geekplus.webapp.function.entity.GpFileTransfer;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * 临时文件中转服务
 */
public interface IGpFileTransferService {

    /**
     * 批量上传并生成分享码
     */
    List<Map<String, Object>> upload(MultipartFile[] files,
                                     Integer expireMinutes,
                                     String password,
                                     Integer maxDownloads,
                                     boolean burnAfterRead,
                                     String fingerprint,
                                     String machineId,
                                     HttpServletRequest request) throws Exception;

    Map<String, Object> getPublicInfo(String shareCode);

    void download(String shareCode, String password, HttpServletRequest request, HttpServletResponse response) throws Exception;

    boolean revoke(String shareCode, String fingerprint, String machineId);

    /** 清理过期文件（定时任务） */
    int cleanupExpired();
}
