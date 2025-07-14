// src/main/java/com/tradestream/auth/security/InternalCallerInterceptor.java
package com.tradestream.auth.security;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class InternalCallerInterceptor implements HandlerInterceptor {

    private static final String INTERNAL_HEADER = "X-Internal-Caller";
    private static final String EXPECTED_VALUE = "api-gateway";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader(INTERNAL_HEADER);
        if (EXPECTED_VALUE.equals(header)) {
            return true; // allow request
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
        response.getWriter().write("Forbidden: Invalid internal caller header.");
        return false; // block request
    }
}
