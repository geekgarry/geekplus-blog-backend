package com.geekplus.webapp.system.controller;

import com.geekplus.common.annotation.Log;
import com.geekplus.common.annotation.RepeatSubmit;
import com.geekplus.common.config.WebAppConfig;
import com.geekplus.common.constant.Constant;
import com.geekplus.common.domain.Result;
import com.geekplus.common.enums.BusinessType;
import com.geekplus.common.util.file.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * author     : geekplus
 * date       : 11/29/23 04:08
 * description:
 */
@RestController
@RequestMapping("/sysFile")
public class SysFileController {

    @Autowired
    private WebAppConfig appConfig;
    /**
     * 查询文件里的所有文件，批量删除某个文件
     */
    @Log(title = "删除文件夹里的文件", businessType = BusinessType.DELETE)
    @PostMapping("/deleteSelectedFiles")
    @RepeatSubmit
    public Result deleteSelectedFile(@RequestBody List<Map> filePaths)
    {
        int length=filePaths.size();
        for (int i = 0; i < filePaths.size(); i++) {
            String filePath=filePaths.get(i).get("filePath").toString();
            String allFilePath=appConfig.getProfile()+filePath.replace(Constant.RESOURCE_PREFIX,"");
            if(filePath.contains(appConfig.getProfile())) {
                allFilePath=filePath;
            }
            int ds=FileUtils.deleteFileByRecursion(allFilePath);
            length-=ds;
        }
        if(length==0){
            return Result.success("删除文件成功！");
        }else{
            return Result.success("删除文件失败！");
        }
    }

    /**
     * 查询文件目录里的所有文件，递归删除文件
     */
    @Log(title = "删除文件夹里的文件", businessType = BusinessType.DELETE)
    @GetMapping("/deleteFileByRecursion")
    @RepeatSubmit
    public Result deleteFileByRecursion(String filePath)
    {
        String allFilePath=appConfig.getProfile()+filePath.replace(Constant.RESOURCE_PREFIX,"");
        if(filePath.contains(appConfig.getProfile())) {
            allFilePath=filePath;
        }
        int count=FileUtils.deleteFileByRecursion(allFilePath);
        if(count>0){
            return Result.success("删除文件成功！");
        }else{
            return Result.success("删除文件失败！");
        }
    }

    /**
     * @Author geekplus
     * @Description //读取文件夹下的所有文件和文件夹
     * @Param
     * @Throws
     * @Return {@link }
     */
    @GetMapping("/readCurrentFileList")
    public Result readCurrentFileList(String folder) throws IOException {
        String readFolder=resolvePath(folder);
        //List<Map> mapList=FileUtils.readFileList(WebAppConfig.getProfile()+File.separator +folder);
        File file = new File(readFolder);
        List<Map<String,Object>> mapList=FileUtils.getAllFileDirectoryInfo(file);
        return Result.success(mapList);
    }

    /**
     * 查询文件里的所有图片，读取某个文件夹下的所有文件
     */
    @GetMapping("/getImageList")
    public Result listFileImage(String fileFolder)
    {
//        try{
//            List<String> listImage= FileUtils.readFileImage(WebAppConfig.getProfile(), File.separator + fileFolder);
//            return Result.success(listImage);
//        }catch(IOException e){
//            return Result.success(e.getMessage());
//        }
        String readFolder=resolvePath(fileFolder);
        File file=new File(readFolder);
        List<String> list= new ArrayList<>();
        FileUtils.getDirectoryAllFile(file,list);
        return Result.success(list);
    }

    public String resolvePath(String fileFolder) {
        String readFolder=appConfig.getProfile()+fileFolder;
        if(fileFolder==null||"".equals(fileFolder)) {
            readFolder=appConfig.getProfile();
        }if(fileFolder=="/"||"/".equals(fileFolder)) {
            readFolder=appConfig.getProfile();
        }else if(fileFolder.contains(appConfig.getProfile())) {
            readFolder=fileFolder;
        }
        return readFolder;
    }
}
