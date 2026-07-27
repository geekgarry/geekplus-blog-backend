package com.geekplus.webapp.function.service.impl;

import com.geekplus.common.config.WebAppConfig;
import com.geekplus.common.util.http.IPUtils;
import com.geekplus.common.util.uuid.UUIDUtil;
import com.geekplus.webapp.function.entity.GpFileTransfer;
import com.geekplus.webapp.function.mapper.GpFileTransferMapper;
import com.geekplus.webapp.function.service.IGpFileTransferService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 临时文件中转：免登录上传、限流、到期清理
 */
@Service
public class GpFileTransferServiceImpl implements IGpFileTransferService {

    /** 单文件上限：200,000,000 字节（对齐瞬匣类产品） */
    public static final long MAX_FILE_BYTES = 200_000_000L;
    /** 单次最多文件数 */
    public static final int MAX_FILES_PER_BATCH = 5;
    /** 同一指纹/IP/机器号 24h 内最多上传条数 */
    public static final int MAX_UPLOADS_PER_DAY = 20;

    private static final int[] ALLOWED_EXPIRE_MINUTES = {15, 60, 360, 1440};

    @Autowired
    private GpFileTransferMapper transferMapper;

    @Override
    public List<Map<String, Object>> upload(MultipartFile[] files,
                                            Integer expireMinutes,
                                            String password,
                                            Integer maxDownloads,
                                            boolean burnAfterRead,
                                            String fingerprint,
                                            String machineId,
                                            HttpServletRequest request) throws Exception {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        if (files.length > MAX_FILES_PER_BATCH) {
            throw new IllegalArgumentException("一次最多上传 " + MAX_FILES_PER_BATCH + " 个文件");
        }
        int expire = normalizeExpire(expireMinutes);
        String ip = IPUtils.getIpAddr(request);
        Date since = new Date(System.currentTimeMillis() - 24L * 3600_000);
        int used = transferMapper.countUploadsSince(
                emptyToNull(fingerprint), emptyToNull(ip), emptyToNull(machineId), since);
        if (used + files.length > MAX_UPLOADS_PER_DAY) {
            throw new IllegalArgumentException("今日上传次数已达上限，请稍后再试");
        }

        String pwdHash = null;
        int hasPwd = 0;
        if (StringUtils.hasText(password)) {
            pwdHash = hashPassword(password.trim());
            hasPwd = 1;
        }
        int maxDl = maxDownloads == null || maxDownloads < 0 ? 0 : maxDownloads;
        Date expireAt = new Date(System.currentTimeMillis() + expire * 60_000L);

        String dayDir = "transfer/" + new java.text.SimpleDateFormat("yyyy/MM/dd").format(new Date());
        String absBase = WebAppConfig.getUploadPath() + "/" + dayDir;
        File dir = new File(absBase);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建存储目录");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("文件超过 200MB 限制: " + file.getOriginalFilename());
            }
            String original = file.getOriginalFilename();
            if (!StringUtils.hasText(original)) {
                original = "file.bin";
            }
            String ext = "";
            int dot = original.lastIndexOf('.');
            if (dot > -1 && dot < original.length() - 1) {
                ext = original.substring(dot);
            }
            String shareCode = UUIDUtil.fastSimpleUUID().substring(0, 12);
            String storedName = shareCode + ext;
            String relative = dayDir + "/" + storedName;
            File dest = new File(absBase, storedName);
            file.transferTo(dest);

            GpFileTransfer row = new GpFileTransfer();
            row.setShareCode(shareCode);
            row.setOriginalName(original);
            row.setStoredName(storedName);
            row.setStoredPath(relative);
            row.setFileSize(file.getSize());
            row.setContentType(file.getContentType());
            row.setPasswordHash(pwdHash);
            row.setHasPassword(hasPwd);
            row.setMaxDownloads(maxDl);
            row.setDownloadCount(0);
            row.setBurnAfterRead(burnAfterRead ? 1 : 0);
            row.setExpireAt(expireAt);
            row.setClientIp(ip);
            row.setFingerprint(emptyToNull(fingerprint));
            row.setMachineId(emptyToNull(machineId));
            row.setStatus(1);
            transferMapper.insertGpFileTransfer(row);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("shareCode", shareCode);
            item.put("originalName", original);
            item.put("fileSize", file.getSize());
            item.put("hasPassword", hasPwd == 1);
            item.put("expireAt", expireAt.getTime());
            item.put("expireMinutes", expire);
            item.put("maxDownloads", maxDl);
            item.put("burnAfterRead", burnAfterRead);
            item.put("path", "/file-transfer/d/" + shareCode);
            results.add(item);
        }
        if (results.isEmpty()) {
            throw new IllegalArgumentException("没有有效的上传文件");
        }
        return results;
    }

    @Override
    public Map<String, Object> getPublicInfo(String shareCode) {
        GpFileTransfer row = requireActive(shareCode);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("shareCode", row.getShareCode());
        info.put("originalName", row.getOriginalName());
        info.put("fileSize", row.getFileSize());
        info.put("hasPassword", row.getHasPassword() != null && row.getHasPassword() == 1);
        info.put("expireAt", row.getExpireAt() != null ? row.getExpireAt().getTime() : null);
        info.put("maxDownloads", row.getMaxDownloads());
        info.put("downloadCount", row.getDownloadCount());
        info.put("burnAfterRead", row.getBurnAfterRead() != null && row.getBurnAfterRead() == 1);
        info.put("remainingDownloads", remainingDownloads(row));
        return info;
    }

    @Override
    public void download(String shareCode, String password, HttpServletRequest request, HttpServletResponse response) throws Exception {
        GpFileTransfer row = requireActive(shareCode);
        if (row.getHasPassword() != null && row.getHasPassword() == 1) {
            if (!StringUtils.hasText(password) || !hashPassword(password.trim()).equals(row.getPasswordHash())) {
                throw new IllegalArgumentException("访问密码错误");
            }
        }
        if (row.getMaxDownloads() != null && row.getMaxDownloads() > 0
                && row.getDownloadCount() != null && row.getDownloadCount() >= row.getMaxDownloads()) {
            throw new IllegalArgumentException("下载次数已用尽");
        }

        File file = new File(WebAppConfig.getUploadPath() + "/" + row.getStoredPath());
        if (!file.exists() || !file.isFile()) {
            throw new IllegalStateException("文件已失效或不存在");
        }

        transferMapper.increaseDownloadCount(row.getId());
        if (row.getBurnAfterRead() != null && row.getBurnAfterRead() == 1) {
            transferMapper.softDeleteById(row.getId());
            // 阅后即焚：本次仍可下载，随后删除磁盘
        }

        String encoded = URLEncoder.encode(row.getOriginalName(), StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
        response.reset();
        response.setContentType(StringUtils.hasText(row.getContentType()) ? row.getContentType() : "application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        response.setContentLengthLong(file.length());
        try (FileInputStream in = new FileInputStream(file); OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
        }

        if (row.getBurnAfterRead() != null && row.getBurnAfterRead() == 1) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Override
    public boolean revoke(String shareCode, String fingerprint, String machineId) {
        GpFileTransfer row = transferMapper.selectByShareCode(shareCode);
        if (row == null || row.getStatus() == null || row.getStatus() != 1) {
            return false;
        }
        // 仅允许同一指纹或机器号撤销自己的分享
        boolean ok = (StringUtils.hasText(fingerprint) && fingerprint.equals(row.getFingerprint()))
                || (StringUtils.hasText(machineId) && machineId.equals(row.getMachineId()));
        if (!ok) {
            return false;
        }
        transferMapper.softDeleteById(row.getId());
        try {
            File disk = new File(WebAppConfig.getUploadPath() + "/" + row.getStoredPath());
            if (disk.exists()) {
                //noinspection ResultOfMethodCallIgnored
                disk.delete();
            }
        } catch (Exception ignore) {
        }
        return true;
    }

    @Override
    public int cleanupExpired() {
        Date now = new Date();
        List<GpFileTransfer> list = transferMapper.selectExpiredActive(now, 200);
        int n = 0;
        for (GpFileTransfer row : list) {
            try {
                File disk = new File(WebAppConfig.getUploadPath() + "/" + row.getStoredPath());
                if (disk.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    disk.delete();
                }
            } catch (Exception ignore) {
            }
            transferMapper.softDeleteById(row.getId());
            n++;
        }
        return n;
    }

    private GpFileTransfer requireActive(String shareCode) {
        if (!StringUtils.hasText(shareCode)) {
            throw new IllegalArgumentException("无效的分享码");
        }
        GpFileTransfer row = transferMapper.selectByShareCode(shareCode.trim());
        if (row == null || row.getStatus() == null || row.getStatus() != 1) {
            throw new IllegalArgumentException("链接不存在或已失效");
        }
        if (row.getExpireAt() != null && row.getExpireAt().before(new Date())) {
            transferMapper.softDeleteById(row.getId());
            throw new IllegalArgumentException("链接已过期");
        }
        return row;
    }

    private int remainingDownloads(GpFileTransfer row) {
        if (row.getMaxDownloads() == null || row.getMaxDownloads() <= 0) {
            return -1;
        }
        int used = row.getDownloadCount() == null ? 0 : row.getDownloadCount();
        return Math.max(0, row.getMaxDownloads() - used);
    }

    private int normalizeExpire(Integer expireMinutes) {
        int v = expireMinutes == null ? 60 : expireMinutes;
        for (int allowed : ALLOWED_EXPIRE_MINUTES) {
            if (allowed == v) {
                return v;
            }
        }
        return 60;
    }

    private String hashPassword(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(("gp-transfer|" + raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("password hash failed", e);
        }
    }

    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
