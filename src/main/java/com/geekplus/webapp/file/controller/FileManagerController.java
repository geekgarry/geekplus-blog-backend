package com.geekplus.webapp.file.controller;

import com.geekplus.common.core.controller.BaseController;
import com.geekplus.common.domain.Result;
import com.geekplus.common.page.PageData;
import com.geekplus.common.page.TableDataSupport;
import com.geekplus.common.util.http.ServletUtil;
import com.geekplus.common.util.string.StringUtils;
import com.geekplus.webapp.file.entity.BatchOperationReq;
import com.geekplus.webapp.file.entity.FileInfo;
import com.geekplus.webapp.file.service.FileService;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/**
 * author     : geekplus
 * email      :
 * date       : 3/26/26 2:51 AM
 * description: //TODO
 */
@RestController
@RequestMapping("/sys/file-manager")
public class FileManagerController extends BaseController {

    @Autowired
    private FileService fileService;

    //构造函数注入
//    private final FileService fileService;
//    public FileManagerController(FileService fileService) {
//        this.fileService = fileService;
//    }

    @GetMapping("/list")
    public Result list(@RequestParam(defaultValue = "/") String path) {
        List<FileInfo> fileInfos = fileService.listFiles(path);
        Result result = Result.success();
        result.put("total", fileInfos.size());
        result.put("data",startPage(fileInfos));
        return result;
    }

    @GetMapping("/search")
    public Result search(@RequestParam(defaultValue = "/") String path, @RequestParam String keyword) {
        List<FileInfo> fileInfoList = fileService.searchFiles(path, keyword);
        if(fileInfoList.size() == 0) {
            return Result.error("搜索文件不存在！");
        }
        Result result = Result.success();
        result.put("total", fileInfoList.size());
        result.put("data", startPage(fileInfoList));
        return result;
    }

    @PostMapping("/rename")
    public Result rename(@RequestParam String path, @RequestParam String newName) {
        boolean success = fileService.rename(path, newName);
        return success ? Result.success() : Result.error("重命名失败");
    }

    @PostMapping("/delete/batch")
    public Result deleteBatch(@RequestBody BatchOperationReq req) {
        try {
            fileService.deleteBatch(req.getPaths(), req.getHardDelete());
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/delete")
    public Result delete(@RequestParam String path) {
        boolean success = fileService.delete(path);
        return success ? Result.success() : Result.error("删除失败");
    }

    @PostMapping("/copy")
    public Result copy(@RequestBody BatchOperationReq req) {
        try {
            fileService.copyBatch(req.getPaths(), req.getDestPath());
            return Result.success("复制成功");
        } catch (Exception e) {
            return Result.error("复制失败: " + e.getMessage());
        }
    }

    @PostMapping("/move")
    public Result move(@RequestBody BatchOperationReq req) {
        try {
            fileService.moveBatch(req.getPaths(), req.getDestPath());
            return Result.success("移动成功");
        } catch (Exception e) {
            return Result.error("移动失败: " + e.getMessage());
        }
    }

    @PostMapping("/compress")
    public Result compress(@RequestBody BatchOperationReq req) {
        try {
            fileService.compressBatch(req.getPaths(), req.getDestPath(), req.getZipName());
            return Result.success("压缩成功");
        } catch (Exception e) {
            return Result.error("压缩失败: " + e.getMessage());
        }
    }

    // 预览/下载接口 (略，通常使用 ResponseEntity<Resource> 返回文件流)
    @PostMapping("/upload")
    public Result upload(@RequestParam("file") MultipartFile file,
                            @RequestParam(defaultValue = "/") String path,
                         @RequestParam(defaultValue = "false") boolean overwrite) {
        try {
            fileService.uploadFile(file, path, overwrite);
            return Result.success("上传成功");
        } catch (IOException e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam String path,
                                                 @RequestParam(defaultValue = "false") boolean preview) throws Exception {
        Resource resource = fileService.loadFileAsResource(path);
        String contentType = fileService.getContentType(path);

        // 处理中文文件名乱码
        String filename = URLEncoder.encode(resource.getFilename(), "UTF-8").replaceAll("\\+", "%20");

        // preview=true 时，Content-Disposition 为 inline (浏览器尝试直接打开，如图片、PDF)
        // preview=false 时，Content-Disposition 为 attachment (强制下载)
        String dispositionType = preview ? "inline" : "attachment";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename=\"" + filename + "\"")
                // 加入 Content-Length 才能让前端知道精确进度并且方便前端流式读取判定长度
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(resource.getFile().length()))
                .body(resource);
    }

    // ---------------- 回收站接口 ----------------
    @GetMapping("/recycle/list")
    public Result listRecycle() {
        return Result.success(fileService.listRecycleBin());
    }

    @PostMapping("/recycle/restore")
    public Result restoreRecycle(@RequestBody List<String> recycleNames) {
        try {
            fileService.restoreRecycle(recycleNames);
            return Result.success("还原成功");
        } catch (Exception e) {
            return Result.error("还原失败: " + e.getMessage());
        }
    }

    @PostMapping("/recycle/delete")
    public Result hardDeleteRecycle(@RequestBody List<String> recycleNames) {
        try {
            fileService.hardDeleteRecycle(recycleNames);
            return Result.success("彻底删除成功");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/recycle/download")
    public ResponseEntity<Resource> downloadRecycleFile(@RequestParam String recycleName) throws Exception {
        Resource resource = fileService.loadRecycleResource(recycleName);

        // 解析原文件名用于下载显示
        String originalName = recycleName;
        String[] parts = recycleName.split("_", 3);
        if (parts.length == 3) originalName = parts[2];

        String filename = URLEncoder.encode(originalName, "UTF-8").replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                // 加入 Content-Length 才能让前端知道精确进度并且方便前端流式读取判定长度
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(resource.getFile().length()))
                .body(resource);
    }

    // ---------------- 新建接口 ----------------
    @PostMapping("/create")
    public Result create(@RequestParam(defaultValue = "/") String path,
                            @RequestParam String name,
                            @RequestParam boolean isFolder) {
        try {
            fileService.create(path, name, isFolder);
            return Result.success(isFolder ? "文件夹创建成功" : "文件创建成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    @PostMapping("/check-exist")
    public Result checkExist(@RequestBody Map<String, Object> body) {
        return Result.success(fileService.checkExist((String) body.get("path"), (String) body.get("filename")));
    }

    @PostMapping("/read-text")
    public Result readTextFile(@RequestParam("path") String path) {
        String content = fileService.readTextFile(path);
        Result result = Result.success();
        result.put("data", content);
        return result;
    }

    @PostMapping("/save-text")
    public Result saveTextFile(@RequestBody Map<String, Object> body) {
        fileService.saveTextFile((String) body.get("path"), (String) body.get("content"));
        return Result.success();
    }
}
