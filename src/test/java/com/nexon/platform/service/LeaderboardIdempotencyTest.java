package com.nexon.platform.service;

import com.nexon.platform.service.IdempotencyService.AcquireResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LeaderboardIdempotencyTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String TEST_ACTION = "test-submit-score";
    private static final String TEST_KEY = "req-uuid-12345";
    private static final String REDIS_KEY = "idempotency:" + TEST_ACTION + ":" + TEST_KEY;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(REDIS_KEY);
    }

    @Test
    @DisplayName("멱등성 상태 머신: 최초 요청 선점(ACQUIRED) -> 처리 완료(COMPLETED) -> 재인입 시 스킵(COMPLETED) 검증")
    void idempotency_Lifecycle_Success() {
        // 1. 최초 요청 인입 -> ACQUIRED 선점 성공
        AcquireResult firstTry = idempotencyService.tryAcquire(TEST_ACTION, TEST_KEY);
        assertThat(firstTry).isEqualTo(AcquireResult.ACQUIRED);

        // 2. 현재 처리 중(IN_PROGRESS) 상태에서 동일 키 동시 인입 -> IN_PROGRESS 반환 (409 대상)
        AcquireResult concurrentTry = idempotencyService.tryAcquire(TEST_ACTION, TEST_KEY);
        assertThat(concurrentTry).isEqualTo(AcquireResult.IN_PROGRESS);

        // 3. 비즈니스 로직 정상 완료 후 COMPLETED 마킹
        idempotencyService.markCompleted(TEST_ACTION, TEST_KEY, 300);

        // 4. 완료 후 동일 키 재인입 -> COMPLETED 반환 (200 OK 바이패스 대상)
        AcquireResult retryAfterComplete = idempotencyService.tryAcquire(TEST_ACTION, TEST_KEY);
        assertThat(retryAfterComplete).isEqualTo(AcquireResult.COMPLETED);
    }

    @Test
    @DisplayName("예외 발생 시 롤백(rollback)을 호출하면 키가 삭제되어 재시도가 가능해야 한다")
    void idempotency_Rollback_Success() {
        // 1. 최초 선점
        idempotencyService.tryAcquire(TEST_ACTION, TEST_KEY);

        // 2. 예외 발생 시나리오 -> 롤백 실행
        idempotencyService.rollback(TEST_ACTION, TEST_KEY);

        // 3. 키 삭제 확인 후 다시 선점 시 ACQUIRED 성공
        AcquireResult retry = idempotencyService.tryAcquire(TEST_ACTION, TEST_KEY);
        assertThat(retry).isEqualTo(AcquireResult.ACQUIRED);
    }
}