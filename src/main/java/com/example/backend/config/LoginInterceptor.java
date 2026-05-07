package com.example.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    public static final String SESSION_USER_ID = "LOGIN_USER_ID";
    public static final String SESSION_USERNAME = "LOGIN_USERNAME";

    @Value("${app.security.dev-auto-login:false}")
    private boolean devAutoLogin;

    @Value("${app.security.dev-auto-login-user-id:1}")
    private Long devAutoLoginUserId;

    @Value("${app.security.dev-auto-login-username:local-dev}")
    private String devAutoLoginUsername;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth/") || uri.startsWith("/api/test/")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(SESSION_USER_ID);
        if (userId == null && shouldAutoLoginForLocalDev(request)) {
            session = request.getSession(true);
            session.setAttribute(SESSION_USER_ID, devAutoLoginUserId);
            session.setAttribute(SESSION_USERNAME, devAutoLoginUsername);
            request.setAttribute(SESSION_USER_ID, devAutoLoginUserId);
            return true;
        }
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"unauthorized\",\"detail\":\"Please login first\"}");
            return false;
        }

        request.setAttribute(SESSION_USER_ID, userId);
        return true;
    }

    private boolean shouldAutoLoginForLocalDev(HttpServletRequest request) {
        if (!devAutoLogin || devAutoLoginUserId == null) {
            return false;
        }
        String remoteAddr = request.getRemoteAddr();
        String serverName = request.getServerName();
        return "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || "localhost".equalsIgnoreCase(serverName);
    }
}
