package com.geekplus.common.config;

import com.geekplus.common.util.http.ServletUtil;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * author     : geekplus
 * description: 项目基本配置加载,在配置文件中定义,服务相关配置
 */
@Component
@ConfigurationProperties(prefix = "geekplus")
public class WebAppConfig {
    /** 项目名称 */
    private static String name;

    /** 版本 */
    private static String version;

    /** 版权年份 */
    private static String copyrightYear;

    /** 实例演示开关 */
    private static boolean demoEnabled;

    /** 服务器路径 */
    private static String profile;

    /** 获取地址开关 */
    private static boolean addressEnabled;

    public static String getName() {
        return name;
    }

    public void setName(String name) {
        WebAppConfig.name = name;
    }

    public static String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        WebAppConfig.version = version;
    }

    public static String getCopyrightYear() {
        return copyrightYear;
    }

    public void setCopyrightYear(String copyrightYear) {
        WebAppConfig.copyrightYear = copyrightYear;
    }

    public static boolean isDemoEnabled() {
        return demoEnabled;
    }

    public void setDemoEnabled(boolean demoEnabled) {
        WebAppConfig.demoEnabled = demoEnabled;
    }

    public static String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        WebAppConfig.profile = profile;
    }

    public static boolean isAddressEnabled() {
        return addressEnabled;
    }

    public void setAddressEnabled(boolean addressEnabled) {
        WebAppConfig.addressEnabled = addressEnabled;
    }

    /**
     * 获取头像上传路径
     */
    public static String getAvatarPath()
    {
        return getUploadPath() + "/avatar";
    }

    /**
     * 获取下载路径
     */
    public static String getDownloadPath()
    {
        return getProfile() + "/download/";
    }

    /**
     * 获取上传路径
     */
    public static String getUploadPath()
    {
        return getProfile() + "/upload";
    }

    /**
     * 获取完整的请求路径，包括：域名，端口，上下文访问路径
     *
     * @return 服务地址
     */
    public static String getUrl()
    {
        HttpServletRequest request = ServletUtil.getRequest();
        return getDomain(request);
    }

    public static String getDomain(HttpServletRequest request)
    {
        StringBuffer url = request.getRequestURL();
        String contextPath = request.getServletContext().getContextPath();
        return url.delete(url.length() - request.getRequestURI().length(), url.length()).append(contextPath).toString();
    }
}
