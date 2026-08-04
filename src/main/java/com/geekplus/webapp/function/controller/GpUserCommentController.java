package com.geekplus.webapp.function.controller;

import com.geekplus.common.annotation.Log;
import com.geekplus.common.core.controller.BaseController;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.domain.Result;
import com.geekplus.common.page.PageDataInfo;
import com.geekplus.common.enums.BusinessType;
import com.geekplus.common.util.ContentDataScopeUtils;
import com.geekplus.common.util.poi.ExcelUtil;
import com.geekplus.webapp.function.entity.GpUserComment;
import com.geekplus.webapp.function.service.IGpUserCommentService;
import com.geekplus.webapp.function.service.ISiteStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/geekplus/comment")
public class GpUserCommentController extends BaseController
{
    @Autowired
    private IGpUserCommentService gpUserCommentService;

    @Autowired(required = false)
    private ISiteStatsService siteStatsService;

    @GetMapping("/list")
    public PageDataInfo list(GpUserComment gpUserComment)
    {
        applyOwnScope(gpUserComment);
        startPage();
        if (gpUserComment.getOwnUserIds() == null || gpUserComment.getOwnUserIds().isEmpty()) {
            if (gpUserComment.getParentId() == null) {
                gpUserComment.setParentId(0L);
            }
        }
        List<GpUserComment> list = gpUserCommentService.selectGpUserCommentList(gpUserComment);
        return getDataTable(list);
    }

    private void applyOwnScope(GpUserComment gpUserComment) {
        LoginUser loginUser = getLoginUser();
        if (!ContentDataScopeUtils.isBlogSiteAdmin(loginUser)) {
            gpUserComment.setOwnUserIds(ContentDataScopeUtils.ownCommentUserIds(loginUser));
            gpUserComment.setParentId(null);
        }
    }

    @GetMapping("/userComment")
    public Result userComment(GpUserComment gpUserComment)
    {
        List<GpUserComment> list = gpUserCommentService.getUserComment(gpUserComment);
        return Result.success(list);
    }

    @Log(title = "用户评论回复留言", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public Result export(GpUserComment gpUserComment)
    {
        applyOwnScope(gpUserComment);
        List<GpUserComment> list = gpUserCommentService.selectGpUserCommentList(gpUserComment);
        ExcelUtil<GpUserComment> util = new ExcelUtil<GpUserComment>(GpUserComment.class);
        return util.exportExcel(list, "comment");
    }

    @GetMapping(value = "/{id}")
    public Result getInfo(@PathVariable("id") Long id)
    {
        LoginUser loginUser = getLoginUser();
        GpUserComment db = gpUserCommentService.selectGpUserCommentById(id);
        if (db != null && !ContentDataScopeUtils.ownsComment(loginUser, db.getUserId())) {
            return Result.error("无权查看该留言");
        }
        return Result.success(db);
    }

    @Log(title = "用户评论回复留言", businessType = BusinessType.INSERT)
    @PostMapping
    public Result add(@RequestBody GpUserComment gpUserComment)
    {
        Result result = toResult(gpUserCommentService.insertGpUserComment(gpUserComment));
        if (siteStatsService != null) {
            try {
                siteStatsService.recordNewComment();
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    @Log(title = "用户评论回复留言", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result edit(@RequestBody GpUserComment gpUserComment)
    {
        LoginUser loginUser = getLoginUser();
        if (gpUserComment.getId() != null) {
            GpUserComment db = gpUserCommentService.selectGpUserCommentById(gpUserComment.getId());
            if (db == null) {
                return Result.error("留言不存在");
            }
            if (!ContentDataScopeUtils.ownsComment(loginUser, db.getUserId())) {
                return Result.error("无权修改他人留言");
            }
        }
        return toResult(gpUserCommentService.updateGpUserComment(gpUserComment));
    }

    @Log(title = "用户评论回复留言", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public Result remove(@PathVariable Long[] ids)
    {
        LoginUser loginUser = getLoginUser();
        List<Long> allowed = new ArrayList<>();
        for (Long id : ids) {
            GpUserComment db = gpUserCommentService.selectGpUserCommentById(id);
            if (db != null && ContentDataScopeUtils.ownsComment(loginUser, db.getUserId())) {
                allowed.add(id);
            }
        }
        if (allowed.isEmpty()) {
            return Result.error("无权删除所选留言");
        }
        return toResult(gpUserCommentService.deleteGpUserCommentByIds(allowed.toArray(new Long[0])));
    }
}
