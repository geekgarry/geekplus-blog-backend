package com.geekplus.webapp.function.controller;

import com.geekplus.common.util.poi.ExcelUtil;
import com.geekplus.webapp.function.service.IGpArticleTagsService;
import com.geekplus.common.annotation.Log;
import com.geekplus.common.config.WebAppConfig;
import com.geekplus.common.core.controller.BaseController;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.domain.Result;
import com.geekplus.common.page.PageDataInfo;
import com.geekplus.common.enums.BusinessType;
import com.geekplus.common.util.ContentDataScopeUtils;
import com.geekplus.common.util.file.FileUtils;
import com.geekplus.webapp.function.entity.GpArticleTags;
import com.geekplus.webapp.function.entity.GpArticles;
import com.geekplus.webapp.function.service.IGpArticlesService;
import com.geekplus.webapp.function.service.ISiteStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 文章Controller
 *
 * @author 佚名
 * @date 2023-03-12
 */
@RestController
@RequestMapping("/geekplus/articles")
public class GpArticlesController extends BaseController
{
    @Autowired
    private IGpArticlesService gpArticlesService;
    @Resource
    IGpArticleTagsService gpArticleTagsService;
    @Autowired
    WebAppConfig appConfig;
    @Autowired(required = false)
    private ISiteStatsService siteStatsService;

    /**
     * 查询文章列表（非管理员仅本人）
     */
    @GetMapping("/list")
    public PageDataInfo list(GpArticles gpArticles)
    {
        LoginUser loginUser = getLoginUser();
        if (!ContentDataScopeUtils.isBlogSiteAdmin(loginUser)
                && loginUser != null && loginUser.getUserId() != null) {
            gpArticles.setAuthorId(loginUser.getUserId());
        }
        startPage();
        List<GpArticles> list = gpArticlesService.selectGpArticlesList(gpArticles);
        return getDataTable(list);
    }

    /**
     * 导出文章列表
     */
    @Log(title = "文章", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public Result export(GpArticles gpArticles)
    {
        LoginUser loginUser = getLoginUser();
        if (!ContentDataScopeUtils.isBlogSiteAdmin(loginUser)
                && loginUser != null && loginUser.getUserId() != null) {
            gpArticles.setAuthorId(loginUser.getUserId());
        }
        List<GpArticles> list = gpArticlesService.selectGpArticlesList(gpArticles);
        ExcelUtil<GpArticles> util = new ExcelUtil<GpArticles>(GpArticles.class);
        return util.exportExcel(list, "articles");
    }

    /**
     * 获取文章详细信息
     */
    @GetMapping(value = "/{id}")
    public Result getInfo(@PathVariable("id") Long id)
    {
        LoginUser loginUser = getLoginUser();
        GpArticles article = gpArticlesService.selectGpArticlesById(id);
        if (article != null && !ContentDataScopeUtils.ownsArticle(loginUser, article.getAuthorId())) {
            return Result.error("无权查看该文章");
        }
        return Result.success(article);
    }

    /**
     * 新增文章
     */
    @Log(title = "新增文章", businessType = BusinessType.INSERT)
    @PostMapping
    public Result add(@RequestBody GpArticles gpArticles)
    {
        LoginUser loginUser = getLoginUser();
        if (loginUser != null && gpArticles.getAuthorId() == null) {
            gpArticles.setAuthorId(loginUser.getUserId());
        }
        Result result = toResult(gpArticlesService.insertGpArticles(gpArticles));
        result.put("articleId", gpArticles.getId());
        if (siteStatsService != null) {
            try {
                siteStatsService.recordNewArticle();
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    /**
     * 修改文章（非管理员仅本人）
     */
    @Log(title = "修改文章", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result edit(@RequestBody GpArticles gpArticles)
    {
        LoginUser loginUser = getLoginUser();
        if (gpArticles.getId() != null) {
            GpArticles db = gpArticlesService.selectGpArticlesById(gpArticles.getId());
            if (db == null) {
                return Result.error("文章不存在");
            }
            if (!ContentDataScopeUtils.ownsArticle(loginUser, db.getAuthorId())) {
                return Result.error("无权修改他人文章");
            }
        }
        return toResult(gpArticlesService.updateGpArticles(gpArticles));
    }

    /**
     * 删除文章（非管理员仅本人）
     */
    @Log(title = "删除文章", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public Result remove(@PathVariable Long[] ids)
    {
        LoginUser loginUser = getLoginUser();
        List<Long> allowed = new ArrayList<>();
        for (Long id : ids) {
            GpArticles db = gpArticlesService.selectGpArticlesById(id);
            if (db != null && ContentDataScopeUtils.ownsArticle(loginUser, db.getAuthorId())) {
                allowed.add(id);
            }
        }
        if (allowed.isEmpty()) {
            return Result.error("无权删除所选文章");
        }
        Long[] allowIds = allowed.toArray(new Long[0]);
        int rows = gpArticlesService.deleteGpArticlesByIds(allowIds);
        if (rows > 0) {
            for (Long id : allowIds) {
                List<GpArticleTags> list = gpArticleTagsService.selectTagByArticleId(String.valueOf(id));
                if (list != null && list.size() > 0) {
                    for (GpArticleTags articleTag : list) {
                        Map map = new HashMap();
                        map.put("aticleId", id);
                        map.put("articleTag", articleTag.getId());
                        gpArticleTagsService.deleteGpArticleMapTagByIdTag(map);
                    }
                }
            }
            return Result.success();
        } else {
            return Result.error();
        }
    }

    @GetMapping("/readFileList")
    public Result readFileList(String folder) throws IOException {
        if (folder == null) {
            folder = "article";
        }
        List<Map> mapList = FileUtils.readFileList(appConfig.getProfile() + File.separator + folder);
        return Result.success(mapList);
    }

    @GetMapping("/getImageList")
    public Result listFileImage(String fileFolder)
    {
        File file = new File(appConfig.getProfile() + File.separator + fileFolder);
        List<String> list = new ArrayList<>();
        FileUtils.getDirectoryAllFile(file, list);
        return Result.success(list);
    }
}
