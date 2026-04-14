package com.geekplus.common.util.regex;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * author     : geekplus
 * email      :
 * date       : 4/15/26 3:41 AM
 * description: //TODO
 */
public class DynamicRegexReplacer {

    /**
     * 通用的正则替换方法
     * @param content 原始内容
     * @param patternStr 要匹配的正则表达式
     * @param replacement 替换后的字符串
     * @return 替换后的内容
     */
    public static String dynamicReplace(String content, String patternStr, String replacement) {
        if (content == null || patternStr == null || replacement == null) {
            return content;
        }

        try {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(content);
            return matcher.replaceAll(replacement);
        } catch (Exception e) {
            // 正则表达式编译失败时，使用字符串替换
            return content.replace(patternStr, replacement);
        }
    }

    /**
     * 批量替换多个模式
     * @param content 原始内容
     * @param replaceMap 替换映射表，key为要匹配的模式，value为替换后的字符串
     * @return 替换后的内容
     */
    public static String batchReplace(String content, Map<String, String> replaceMap) {
        if (content == null || replaceMap == null || replaceMap.isEmpty()) {
            return content;
        }

        String result = content;
        for (Map.Entry<String, String> entry : replaceMap.entrySet()) {
            result = dynamicReplace(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    // 新增方法：将以/profile开头的路径替换为/profile/upload
    public static String replaceProfilePath(String content) {
        if (content == null) {
            return null;
        }
        // 定义正则表达式匹配以'/profile'开头的路径
        Pattern pattern = Pattern.compile("/profile(/[^\\s]*)");
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            // 将匹配到的路径替换为'/profile/upload'开头
            matcher.appendReplacement(sb, "/profile/upload" + matcher.group(1));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 预定义的常用替换模式
     */
    public static class Patterns {
        // xxx.xxx域名（支持http和https）
        public static final String GEEKPLUS_DOMAIN = "https?://(www\\.)?xxx\\.xxx";

        // 匹配/profile开头的路径
        public static final String PROFILE_PATH = "/profile(/[^\\s]*)?";

        // 匹配所有URL
        public static final String ALL_URLS = "https?://[^\\s]+";

        // 匹配相对路径
        public static final String RELATIVE_PATHS = "/[^\\s/][^\\s]*";
    }
}
