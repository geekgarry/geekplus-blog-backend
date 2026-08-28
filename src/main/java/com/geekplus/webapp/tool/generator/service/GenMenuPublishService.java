package com.geekplus.webapp.tool.generator.service;

import com.geekplus.webapp.tool.generator.dto.GenPublishMenuPreview;
import com.geekplus.webapp.tool.generator.dto.GenPublishMenuRequest;
import com.geekplus.webapp.tool.generator.dto.GenPublishMenuResult;

/**
 * 代码生成：仅菜单发布（与 ZIP 下载解耦）。
 */
public interface GenMenuPublishService {

    GenPublishMenuPreview previewPublish(Long tableId);

    GenPublishMenuResult publishMenus(GenPublishMenuRequest request, String operator);
}
