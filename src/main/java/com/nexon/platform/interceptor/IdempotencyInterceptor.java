package com.nexon.platform.interceptor;

import com.nexon.platform.annotation.Idempotent;
import com.nexon.platform.service.IdempotencyService;
import com.nexon.platform.service.IdempotencyService.AcquireResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String ATTR_ACTION_NAME = "IDEMPOTENT_ACTION_NAME";
    public static final String ATTR_IDEMPOTENCY_KEY = "IDEMPOTENT_KEY";
    public static final String ATTR_TIMEOUT = "IDEMPOTENT_TIMEOUT";

    private final IdempotencyService idempotencyService;

    public IdempotencyInterceptor(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Idempotent idempotent = handlerMethod.getMethodAnnotation(Idempotent.class);
        if (idempotent == null) {
            return true;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"Idempotency-Key 헤더가 누락되었습니다.\"}");
            return false;
        }

        String actionName = idempotent.name().isBlank() ? handlerMethod.getMethod().getName() : idempotent.name();
        AcquireResult result = idempotencyService.tryAcquire(actionName, idempotencyKey.trim());

        if (result == AcquireResult.IN_PROGRESS) {
            response.setStatus(HttpStatus.CONFLICT.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"동일한 요청이 현재 처리 중입니다. 잠시 후 확인해 주세요.\"}");
            return false;
        }

        if (result == AcquireResult.COMPLETED) {
            // 이미 성공한 요청이므로 컨트롤러 로직을 스킵하고 200 OK 반환
            response.setStatus(HttpStatus.OK.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":true,\"message\":\"이미 처리 완료된 요청입니다 (멱등성 보장).\"}");
            return false;
        }

        // 신규 선점 성공 -> afterCompletion에서 후처리할 수 있도록 request attribute에 보관
        request.setAttribute(ATTR_ACTION_NAME, actionName);
        request.setAttribute(ATTR_IDEMPOTENCY_KEY, idempotencyKey.trim());
        request.setAttribute(ATTR_TIMEOUT, idempotent.timeoutSeconds());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String actionName = (String) request.getAttribute(ATTR_ACTION_NAME);
        String idempotencyKey = (String) request.getAttribute(ATTR_IDEMPOTENCY_KEY);
        Long timeout = (Long) request.getAttribute(ATTR_TIMEOUT);

        if (actionName == null || idempotencyKey == null) {
            return;
        }

        // 요청이 정상 처리(HTTP 2xx)되었으면 COMPLETED 갱신, 에러 발생 시 재시도를 위해 롤백(삭제)
        if (ex == null && response.getStatus() >= 200 && response.getStatus() < 300) {
            idempotencyService.markCompleted(actionName, idempotencyKey, timeout != null ? timeout : 600L);
        } else {
            idempotencyService.rollback(actionName, idempotencyKey);
        }
    }
}