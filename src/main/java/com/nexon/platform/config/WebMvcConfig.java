package com.nexon.platform.config;

import com.nexon.platform.interceptor.IdempotencyInterceptor;
import com.nexon.platform.interceptor.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final IdempotencyInterceptor idempotencyInterceptor;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor,
                          IdempotencyInterceptor idempotencyInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.idempotencyInterceptor = idempotencyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 처리율 제한 인터셉터 (초당 트래픽 제어)
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");

        // 2. 멱등성 검증 인터셉터 (중복 트랜잭션 방어)
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns("/api/**");
    }
}