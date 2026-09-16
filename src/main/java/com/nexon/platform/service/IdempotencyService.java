package com.nexon.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public enum AcquireResult {
        ACQUIRED,       // 신규 요청 선점 성공
        IN_PROGRESS,    // 동일 키로 현재 다른 스레드에서 처리 중 (409 Conflict 대상)
        COMPLETED       // 이미 처리 완료된 요청 (200 OK 바이패스 대상)
    }

    public AcquireResult tryAcquire(String actionName, String idempotencyKey) {
        String redisKey = IDEMPOTENCY_KEY_PREFIX + actionName + ":" + idempotencyKey;

        // 1. SETNX 원자적 실행: 키가 없으면 IN_PROGRESS로 등록 (최대 60초 락)
        Boolean success = redisTemplate.opsForValue().setIfAbsent(redisKey, STATUS_IN_PROGRESS, 60, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(success)) {
            log.info("[멱등성 키 선점 성공] key={}, status={}", redisKey, STATUS_IN_PROGRESS);
            return AcquireResult.ACQUIRED;
        }

        // 2. 키가 이미 존재할 경우 현재 상태 조회
        String currentStatus = redisTemplate.opsForValue().get(redisKey);
        if (STATUS_COMPLETED.equals(currentStatus)) {
            log.info("[멱등성 완료 요청 재인입 감지] key={}, 비즈니스 로직 스킵", redisKey);
            return AcquireResult.COMPLETED;
        }

        log.warn("[멱등성 동시 요청 경합 감지] key={}, 현재 처리 중", redisKey);
        return AcquireResult.IN_PROGRESS;
    }

    public void markCompleted(String actionName, String idempotencyKey, long timeoutSeconds) {
        String redisKey = IDEMPOTENCY_KEY_PREFIX + actionName + ":" + idempotencyKey;
        redisTemplate.opsForValue().set(redisKey, STATUS_COMPLETED, timeoutSeconds, TimeUnit.SECONDS);
        log.info("[멱등성 처리 완료 갱신] key={}, status={}, ttl={}s", redisKey, STATUS_COMPLETED, timeoutSeconds);
    }

    public void rollback(String actionName, String idempotencyKey) {
        String redisKey = IDEMPOTENCY_KEY_PREFIX + actionName + ":" + idempotencyKey;
        redisTemplate.delete(redisKey);
        log.warn("[멱등성 키 롤백 삭제] key={}, 재시도 가능 상태 복원", redisKey);
    }
}