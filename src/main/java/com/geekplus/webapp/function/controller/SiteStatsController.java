package com.geekplus.webapp.function.controller;

import com.geekplus.common.core.controller.BaseController;
import com.geekplus.common.domain.LoginUser;
import com.geekplus.common.domain.Result;
import com.geekplus.common.util.ContentDataScopeUtils;
import com.geekplus.webapp.function.service.ISiteStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营看板统计（管理员）
 */
@RestController
@RequestMapping("/geekplus/stats")
public class SiteStatsController extends BaseController {

    @Autowired
    private ISiteStatsService siteStatsService;

    @GetMapping("/dashboard")
    public Result dashboard(@RequestParam(value = "days", required = false, defaultValue = "30") Integer days) {
        LoginUser loginUser = getLoginUser();
        if (!ContentDataScopeUtils.isBlogSiteAdmin(loginUser)) {
            return Result.error("无权查看运营看板");
        }
        return Result.success(siteStatsService.buildDashboard(days == null ? 30 : days));
    }
}
