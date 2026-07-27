package com.geekplus.webapp.function.mapper;

import com.geekplus.webapp.function.entity.GpFileTransfer;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 临时文件中转 Mapper
 */
public interface GpFileTransferMapper {

    int insertGpFileTransfer(GpFileTransfer row);

    GpFileTransfer selectByShareCode(@Param("shareCode") String shareCode);

    int increaseDownloadCount(@Param("id") Long id);

    int softDeleteById(@Param("id") Long id);

    int softDeleteExpired(@Param("now") Date now);

    List<GpFileTransfer> selectExpiredActive(@Param("now") Date now, @Param("limit") int limit);

    int countUploadsSince(@Param("fingerprint") String fingerprint,
                          @Param("clientIp") String clientIp,
                          @Param("machineId") String machineId,
                          @Param("since") Date since);
}
