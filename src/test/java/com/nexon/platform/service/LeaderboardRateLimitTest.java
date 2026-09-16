package com.nexon.platform.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LeaderboardRateLimitTest {

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String TEST_ACTION = "test-hall-of-fame";
    private static final String TEST_CLIENT_IP = "192.168.1.100";
    private static final String REDIS_RATE_KEY = "rate:limit:" + TEST_ACTION + ":" + TEST_CLIENT_IP;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(REDIS_RATE_KEY);
    }

    @Test
    @DisplayName("처리율 제한 조건에서 한도까지는 허용되고 초과 요청은 즉시 차단되며, 윈도우 시간 경과 후 다시 허용되어야 한다")
    void rateLimiter_SlidingWindow_Success() throws InterruptedException {
        int limit = 5;
        int periodSeconds = 2; // 테스트 환경(Docker 네트워크 지연 및 스레드 경합)을 고려하여 2초 윈도우로 안정화

        // 1. 5회 연속 요청 -> 전원 성공(true)
        for (int i = 1; i <= limit; i++) {
            boolean allowed = rateLimiterService.tryAcquire(TEST_ACTION, TEST_CLIENT_IP, limit, periodSeconds);
            assertThat(allowed).isTrue();
        }

        // 2. 6회차 즉시 호출 -> 2초 윈도우 내 5회 초과이므로 차단(false)
        boolean sixthAttempt = rateLimiterService.tryAcquire(TEST_ACTION, TEST_CLIENT_IP, limit, periodSeconds);
        assertThat(sixthAttempt).isFalse();

        // 3. 2.2초 대기 (2초 슬라이딩 윈도우 완전히 경과)
        Thread.sleep(2200);

        // 4. 경과 후 재호출 -> 과거 타임스탬프 일괄 삭제 후 정상 통과(true)
        boolean retryAttempt = rateLimiterService.tryAcquire(TEST_ACTION, TEST_CLIENT_IP, limit, periodSeconds);
        assertThat(retryAttempt).isTrue();
    }
}