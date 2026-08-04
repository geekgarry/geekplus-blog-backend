package com.geekplus.framework.interceptor;

import com.geekplus.common.core.visit.VisitCounter;
import com.geekplus.webapp.common.service.IPLimitService;
import com.geekplus.webapp.function.service.ISiteStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;

/**
 * 站点访问统计拦截器（仅前台 geekplusapp，避免管理端/登录接口被反复计数拖慢）
 */
@Component
public class VisitInterceptor implements HandlerInterceptor {

    private static final String VISIT_SESSION_ATTRIBUTE = "hasVisited";
    private static final String VISITOR_COOKIE = "gp_vid";
    private static final long VISIT_THRESHOLD = 5000;

    @Autowired
    private VisitCounter visitCounter;

    @Autowired(required = false)
    private ISiteStatsService siteStatsService;

    @Autowired(required = false)
    private IPLimitService ipLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        // 仅统计前台应用接口；排除自身统计接口与高频轮询
        if (!uri.contains("/geekplusapp/")) {
            return true;
        }
        if (uri.endsWith("/visitInfo") || uri.contains("/visitInfo")) {
            return true;
        }

        String ipAddress = getClientIp(request);
        HttpSession session = request.getSession(false);
        Long lastVisitTime = session == null ? null : (Long) session.getAttribute("lastVisitTime");
        boolean hasVisited = session != null && session.getAttribute(VISIT_SESSION_ATTRIBUTE) != null;
        long currentTime = System.currentTimeMillis();

        if (hasVisited && lastVisitTime != null && (currentTime - lastVisitTime) < VISIT_THRESHOLD) {
            session.setAttribute("lastVisitTime", currentTime);
            return true;
        }

        visitCounter.increment();

        String visitor = resolveVisitorId(request, response, session, ipAddress);
        if (siteStatsService != null) {
            try {
                siteStatsService.trackVisit(visitor);
            } catch (Exception ignored) {
            }
        }

        if (session == null) {
            session = request.getSession(true);
        }
        session.setAttribute(VISIT_SESSION_ATTRIBUTE, true);
        session.setAttribute("lastVisitTime", currentTime);
        return true;
    }

    private String resolveVisitorId(HttpServletRequest request, HttpServletResponse response,
                                    HttpSession session, String ipAddress) {
        if (session != null && session.getId() != null && !session.getId().isEmpty()) {
            return session.getId();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (VISITOR_COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                    return c.getValue();
                }
            }
        }
        String vid = UUID.randomUUID().toString().replace("-", "");
        Cookie cookie = new Cookie(VISITOR_COOKIE, vid);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 365);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        if (ipAddress != null && !ipAddress.isEmpty()) {
            return vid + ":" + ipAddress;
        }
        return vid;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
    }

    private String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        return ipAddress;
    }
}
