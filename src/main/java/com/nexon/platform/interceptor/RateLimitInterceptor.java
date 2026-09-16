package com.nexon.platform.interceptor;

import com.nexon.platform.annotation.RateLimit;
import com.nexon.platform.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;

    public RateLimitInterceptor(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String clientIp = extractClientIp(request);
        String actionName = rateLimit.name().isBlank() ? handlerMethod.getMethod().getName() : rateLimit.name();

        boolean allowed = rateLimiterService.tryAcquire(
                actionName,
                clientIp,
                rateLimit.limit(),
                rateLimit.periodSeconds()
        );

        if (!allowed) {
            // GlobalExceptionHandler에 걸려 500으로 왜곡되지 않도록 HTTP 429를 직접 응답
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"단시간에 너무 많은 요청이 발생했습니다. 잠시 후 다시 시도해 주세요.\"}");
            return false;
        }

        return true;
    }

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}