package com.geekplus.common.core.scheduled;

import com.geekplus.webapp.function.service.IGpFileTransferService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 临时文件中转：定期清理过期文件与记录
 */
@Slf4j
@Component
public class FileTransferCleanupTask {

    @Autowired
    private IGpFileTransferService transferService;

    /** 每 20 分钟清理一批过期文件 */
    @Scheduled(cron = "0 */20 * * * ?")
    public void cleanup() {
        try {
            int n = transferService.cleanupExpired();
            if (n > 0) {
                log.info("file-transfer cleanup removed {} expired item(s)", n);
            }
        } catch (Exception e) {
            log.warn("file-transfer cleanup failed: {}", e.getMessage());
        }
    }
}
