package com.geekplus.webapp.function.entity;

import com.geekplus.common.domain.BaseEntity;

import java.util.Date;

/**
 * 临时文件中转 gp_file_transfer
 */
public class GpFileTransfer extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String shareCode;
    private String originalName;
    private String storedName;
    private String storedPath;
    private Long fileSize;
    private String contentType;
    private String passwordHash;
    private Integer hasPassword;
    private Integer maxDownloads;
    private Integer downloadCount;
    private Integer burnAfterRead;
    private Date expireAt;
    private String clientIp;
    private String fingerprint;
    private String machineId;
    /** 1 有效 0 已删 */
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShareCode() { return shareCode; }
    public void setShareCode(String shareCode) { this.shareCode = shareCode; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Integer getHasPassword() { return hasPassword; }
    public void setHasPassword(Integer hasPassword) { this.hasPassword = hasPassword; }
    public Integer getMaxDownloads() { return maxDownloads; }
    public void setMaxDownloads(Integer maxDownloads) { this.maxDownloads = maxDownloads; }
    public Integer getDownloadCount() { return downloadCount; }
    public void setDownloadCount(Integer downloadCount) { this.downloadCount = downloadCount; }
    public Integer getBurnAfterRead() { return burnAfterRead; }
    public void setBurnAfterRead(Integer burnAfterRead) { this.burnAfterRead = burnAfterRead; }
    public Date getExpireAt() { return expireAt; }
    public void setExpireAt(Date expireAt) { this.expireAt = expireAt; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
