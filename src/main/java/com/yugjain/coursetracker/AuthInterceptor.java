package com.yugjain.coursetracker;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/** Java version of the @login_required decorator. */
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SessionUser.KEY) != null) {
            return true;
        }
        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                "{\"error\":\"Authentication required. Please sign in.\",\"authenticated\":false}");
        } else {
            response.sendRedirect("/");
        }
        return false;
    }
}
