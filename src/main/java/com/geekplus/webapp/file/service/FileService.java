package com.geekplus.webapp.file.service;

import com.geekplus.common.config.WebAppConfig;
import com.geekplus.common.constant.Constant;
import com.geekplus.webapp.file.entity.RecycleFileInfo;
import org.springframework.core.io.Resource;
import com.geekplus.webapp.file.entity.FileInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * author     : geekplus
 * email      :
 * date       : 3/26/26 2:48 AM
 * description: //TODO
 */
@Service
public class FileService {

    //服务器项目文件总地址，对应后端映射的resource地址
    @Value("${geekplus.profile}")
    private String basePath;

    private static final String RECYCLE_BIN = "/.recycle_bin";

    // 获取指定相对路径下的所有文件和文件夹 (过滤掉回收站)
    public List<FileInfo> listFiles(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            relativePath = "/";
        }
        // 安全校验：防止目录穿越攻击 (如 ../../)
        //if (relativePath.contains("..")) {
        //    throw new IllegalArgumentException("非法的路径");
        //}
        checkPath(relativePath);
        File dir = new File(hasBasePath(relativePath));
        if (!dir.exists() || !dir.isDirectory()) {
            return new ArrayList<>();
        }

        File[] files = dir.listFiles(pathname -> !pathname.getName().equals(".recycle_bin"));
        if (files == null) return new ArrayList<>();
        String finalRelativePath = relativePath;
        return Arrays.stream(files).map(file -> buildFileInfo(file, finalRelativePath)).collect(Collectors.toList());
    }

    // 2. 搜索文件 (递归)
    public List<FileInfo> searchFiles(String relativePath, String keyword) {
        checkPath(relativePath);
        List<FileInfo> result = new ArrayList<>();
        Path startPath = Paths.get(hasBasePath(relativePath));

        if (!Files.exists(startPath)) return result;

        try {
            Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toFile().getName().toLowerCase().contains(keyword.toLowerCase())) {
                        String pathStr = file.toFile().getAbsolutePath().replace(new File(basePath).getAbsolutePath(), "").replace("\\", "/");
                        result.add(buildFileInfo(file.toFile(), new File(pathStr).getParent().replace("\\", "/")));
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.toFile().getName().equals(".recycle_bin")) return FileVisitResult.SKIP_SUBTREE;
                    if (dir.toFile().getName().toLowerCase().contains(keyword.toLowerCase()) && !dir.equals(startPath)) {
                        String pathStr = dir.toFile().getAbsolutePath().replace(new File(basePath).getAbsolutePath(), "").replace("\\", "/");
                        result.add(buildFileInfo(dir.toFile(), new File(pathStr).getParent().replace("\\", "/")));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    // ---------------- 批量操作与回收站机制 ----------------
    public void deleteBatch(List<String> paths, boolean hardDelete) throws IOException {
        for (String path : paths) {
            checkPath(path);
            File file = new File(hasBasePath(path));
            if (!file.exists()) continue;

            if (hardDelete) {
                deleteRecursively(file);
            } else {
                // 3. 批量删除 (支持放入回收站)
                // 放入回收站
                // File recycleDir = new File(basePath + RECYCLE_BIN);
                // if (!recycleDir.exists()) recycleDir.mkdirs();
                // 加上时间戳防止重名覆盖
                // File dest = new File(recycleDir, System.currentTimeMillis() + "_" + file.getName());
                // file.renameTo(dest);
                // 放入回收站：使用 Base64 编码原路径，拼接到文件名中
                File recycleDir = new File(basePath + RECYCLE_BIN);
                if (!recycleDir.exists()) recycleDir.mkdirs();

                String encodedPath = Base64.getUrlEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8));
                String recycleName = System.currentTimeMillis() + "_" + encodedPath + "_" + file.getName();

                Files.move(file.toPath(), new File(recycleDir, recycleName).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // 获取回收站列表
    public List<RecycleFileInfo> listRecycleBin() {
        File recycleDir = new File(basePath + RECYCLE_BIN);
        if (!recycleDir.exists() || !recycleDir.isDirectory()) return new ArrayList<>();

        File[] files = recycleDir.listFiles();
        if (files == null) return new ArrayList<>();

        return Arrays.stream(files).map(file -> {
            RecycleFileInfo info = new RecycleFileInfo();
            info.setRecycleName(file.getName());
            info.setSize(file.isDirectory() ? 0L : file.length());
            info.setIsDirectory(file.isDirectory());

            String[] parts = file.getName().split("_", 3);
            if (parts.length == 3) {
                info.setDeleteTime(new Date(Long.parseLong(parts[0])));
                try {
                    info.setOriginalPath(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
                } catch (Exception e) {
                    info.setOriginalPath("解析失败");
                }
                info.setOriginalName(parts[2]);
            } else {
                info.setOriginalName(file.getName());
                info.setOriginalPath("未知");
                info.setDeleteTime(new Date(file.lastModified()));
            }
            return info;
        }).sorted(Comparator.comparing(RecycleFileInfo::getDeleteTime).reversed()).collect(Collectors.toList());
    }

    // 还原回收站文件
    public void restoreRecycle(List<String> recycleNames) throws IOException {
        File recycleDir = new File(basePath + RECYCLE_BIN);
        for (String recycleName : recycleNames) {
            if (recycleName.contains("..") || recycleName.contains("/")) continue;
            File file = new File(recycleDir, recycleName);
            if (!file.exists()) continue;

            String[] parts = recycleName.split("_", 3);
            if (parts.length == 3) {
                String originalPath = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                File dest = new File(basePath + originalPath);
                if (!dest.getParentFile().exists()) dest.getParentFile().mkdirs();
                Files.move(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // 彻底删除回收站文件
    public void hardDeleteRecycle(List<String> recycleNames) {
        File recycleDir = new File(basePath + RECYCLE_BIN);
        for (String recycleName : recycleNames) {
            if (recycleName.contains("..") || recycleName.contains("/")) continue;
            File file = new File(recycleDir, recycleName);
            if (file.exists()) deleteRecursively(file);
        }
    }

    // 获取回收站文件的 Resource (用于下载)
    public Resource loadRecycleResource(String recycleName) throws MalformedURLException {
        if (recycleName.contains("..") || recycleName.contains("/")) throw new IllegalArgumentException("非法路径");
        Path filePath = Paths.get(basePath + RECYCLE_BIN).resolve(recycleName).normalize();
        Resource resource = new UrlResource(filePath.toUri());
        if (resource.exists() && resource.isReadable()) return resource;
        throw new RuntimeException("文件未找到");
    }

    // ---------------- 其他批量操作 (复制/移动/压缩) ----------------
    // 4. 批量复制
    public void copyBatch(List<String> sourcePaths, String destPath) throws IOException {
        checkPath(destPath);
        Path destDir = Paths.get(hasBasePath(destPath));
        if (!Files.exists(destDir)) Files.createDirectories(destDir);

        for (String src : sourcePaths) {
            checkPath(src);
            Path sourceFile = Paths.get(hasBasePath(src));
            if (!Files.exists(sourceFile)) continue;
            copyRecursively(sourceFile, destDir.resolve(sourceFile.getFileName()));
        }
    }

    // 5. 批量移动 (剪切)
    public void moveBatch(List<String> sourcePaths, String destPath) throws IOException {
        checkPath(destPath);
        Path destDir = Paths.get(hasBasePath(destPath));
        if (!Files.exists(destDir)) Files.createDirectories(destDir);

        for (String src : sourcePaths) {
            checkPath(src);
            Path sourceFile = Paths.get(hasBasePath(src));
            if (!Files.exists(sourceFile)) continue;
            Files.move(sourceFile, destDir.resolve(sourceFile.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // 6. 批量压缩
    public void compressBatch(List<String> sourcePaths, String destPath, String zipName) throws IOException {
        checkPath(destPath);
        if (!zipName.endsWith(".zip")) zipName += ".zip";
        File zipFile = new File(hasBasePath(destPath), zipName);

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (String src : sourcePaths) {
                checkPath(src);
                File fileToZip = new File(hasBasePath(src));
                if (fileToZip.exists()) {
                    zipFile(fileToZip, fileToZip.getName(), zos);
                }
            }
        }
    }

    // --- 辅助方法 ---
    private void zipFile(File fileToZip, String fileName, ZipOutputStream zos) throws IOException {
        if (fileToZip.isHidden()) return;
        if (fileToZip.isDirectory()) {
            if (!fileName.endsWith("/")) fileName += "/";
            zos.putNextEntry(new ZipEntry(fileName));
            zos.closeEntry();
            File[] children = fileToZip.listFiles();
            if (children != null) {
                for (File child : children) zipFile(child, fileName + child.getName(), zos);
            }
            return;
        }
        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            zos.putNextEntry(new ZipEntry(fileName));
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) zos.write(bytes, 0, length);
        }
    }

    private void copyRecursively(Path src, Path dest) throws IOException {
        Files.walk(src).forEach(source -> {
            try {
                Path destination = dest.resolve(src.relativize(source));
                if (Files.isDirectory(source)) {
                    if (!Files.exists(destination)) Files.createDirectory(destination);
                } else {
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private boolean deleteRecursively(File root) {
        if (root.isDirectory()) {
            File[] children = root.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        return root.delete();
    }

    private void checkPath(String path) {
        if (path != null && path.contains("..")) throw new IllegalArgumentException("非法路径");
    }

    private FileInfo buildFileInfo(File file, String relativePath) {
        FileInfo info = new FileInfo();
        info.setName(file.getName());
        // 拼接相对路径
        String path = relativePath.endsWith("/") ? relativePath + file.getName() : relativePath + "/" + file.getName();
        info.setPath(path);
        //绝对路径
        info.setFullPath(file.getAbsolutePath());
        //设置当前项目服务器资源路径为映射的网络访问路径
        info.setUrl(file.getAbsolutePath().startsWith(basePath) ? file.getAbsolutePath().replaceAll(basePath, Constant.RESOURCE_PREFIX) : "");
        info.setIsDirectory(file.isDirectory());
        info.setSize(file.isDirectory() ? 0L : file.length());
        info.setUpdateTime(new Date(file.lastModified()));
        if (!file.isDirectory()) {
            String name = file.getName();
            info.setType(name.contains(".") ? name.substring(name.lastIndexOf(".") + 1).toLowerCase() : "unknown");
        } else {
            info.setType("folder");
        }
        return info;
    }

    // 重命名文件或文件夹
    public boolean rename(String oldRelativePath, String newName) {
        if (oldRelativePath.contains("..") || newName.contains("/") || newName.contains("\\")) {
            throw new IllegalArgumentException("非法的路径或名称");
        }
        File oldFile = new File(hasBasePath(oldRelativePath));
        if (!oldFile.exists()) return false;

        String parentPath = oldFile.getParent();
        File newFile = new File(parentPath + File.separator + newName);

        // 可以在这里加入 MyBatis 的 update 逻辑，同步更新数据库中的 file_path 和 file_name
        // sysFileMapper.updatePath(oldRelativePath, newRelativePath);

        return oldFile.renameTo(newFile);
    }

    // 删除文件或文件夹
    public boolean delete(String relativePath) {
        if (relativePath.contains("..")) throw new IllegalArgumentException("非法的路径");
        File file = new File(hasBasePath(relativePath));
        if (!file.exists()) return true;

        // 可以在这里加入 MyBatis 的 delete 逻辑
        // sysFileMapper.deleteByPath(relativePath);

        return deleteRecursively(file);
    }

    // 1. 文件上传
    public void uploadFile(MultipartFile file, String relativePath, boolean overwrite) throws IOException {
        if (relativePath.contains("..")) {
            throw new IllegalArgumentException("非法的路径");
        }
        if (!StringUtils.hasText(relativePath)) {
            relativePath = "/";
        }

        // 确保目标目录存在
        File dir = new File(hasBasePath(relativePath));
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 拼接完整的文件保存路径
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        Path targetLocation = Paths.get(dir.getAbsolutePath()).resolve(originalFilename);
        //Path targetLocation = Paths.get(rootPath, relativePath, fileName).toAbsolutePath().normalize();

        if (!overwrite && targetLocation.toFile().exists()) {
            throw new IllegalArgumentException("文件已存在且不允许覆盖"); // 理论上前端已拦截
        }
        // 保存文件 (如果已存在则覆盖)
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
    }

    // 2. 加载文件资源 (用于下载和预览)
    public Resource loadFileAsResource(String relativePath) {
        if (relativePath.contains("..")) {
            throw new IllegalArgumentException("非法的路径");
        }
        try {
            Path filePath = Paths.get(hasBasePath(relativePath)).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("文件未找到或无法读取: " + relativePath);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("文件路径无效: " + relativePath, ex);
        }
    }

    // 3. 获取文件的 Content-Type
    public String getContentType(String relativePath) {
        try {
            Path filePath = Paths.get(hasBasePath(relativePath)).normalize();
            String contentType = Files.probeContentType(filePath);
            return contentType == null ? "application/octet-stream" : contentType;
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    // ---------------- 新建文件/文件夹 ----------------
    public void create(String relativePath, String name, boolean isFolder) throws IOException {
        checkPath(relativePath);
        checkPath(name); // 防止名称中包含 .. 导致目录穿越

        if (name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("名称不能包含路径分隔符");
        }

        // 拼接目标完整路径
        String targetPath = relativePath.endsWith("/") ? relativePath + name : relativePath + "/" + name;
        File targetFile = new File(hasBasePath(targetPath));

        if (targetFile.exists()) {
            throw new IllegalArgumentException("该名称已存在，请更换名称");
        }

        // 确保父目录存在
        File parent = targetFile.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        if (isFolder) {
            // 创建文件夹
            boolean created = targetFile.mkdirs();
            if (!created) throw new IOException("文件夹创建失败");
        } else {
            // 创建空文件
            boolean created = targetFile.createNewFile();
            if (!created) throw new IOException("文件创建失败");
        }
    }

    //上传检查文件是否存在
    public boolean checkExist(String relativePath, String filename) {
        checkPath(relativePath); checkPath(filename);
        //Path filePath = Paths.get(WebAppConfig.getProfile(), relativePath, filename).toAbsolutePath().normalize();
        //filePath.toFile().exists();
        String targetPath = relativePath.endsWith("/") ? relativePath + filename : relativePath + "/" + filename;
        return new File(hasBasePath(targetPath)).exists();
    }

    public String readTextFile(String relativePath) {
        Path filePath = resolvePath(relativePath);
        try {
            // jdk11的方法
            //Files.readString(filePath, StandardCharsets.UTF_8);
            // 下面是jdk8的替代方案
            // 方案一(快速读取小文本文件)
            //return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            // 方案二(按行处理文本数据，代码简洁)
            return Files.lines(filePath).collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException("读取文本文件失败: " + filePath, e);
        }
    }

    public void saveTextFile(String relativePath, String content) {
        Path filePath = resolvePath(relativePath);
        try {
            // jdk11+的写入内容方法
            //Files.writeString(filePath, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            // 方式1：Files.write + 字节转换
            //Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));

            // 方式2：BufferedWriter（推荐用于大文件或多行写入）
            BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8);
            writer.write(content);
            // flush() 在 close() 时会自动调用，但显式调用有助于调试中间状态
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("保存文本文件失败: " + filePath, e);
        }
    }

    private Path resolvePath(String relativePath) {
        Path path = null;
        Path rootDirectory = Paths.get(basePath);
        if(relativePath.startsWith("/")) {
            return Paths.get(hasBasePath(relativePath));
        } else {
            path = rootDirectory.resolve(relativePath).normalize();
        }

        if (!path.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return path;
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.1f %sB", (double)size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private String hasBasePath(String relativePath) {
        //当找不到项目服务器的总地址时
        if(!relativePath.startsWith(basePath) && !relativePath.contains(basePath)) {
            relativePath = basePath + relativePath;
        }
        return relativePath;
    }
}
