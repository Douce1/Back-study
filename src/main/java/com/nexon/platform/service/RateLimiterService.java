package com.nexon.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final String RATE_LIMIT_KEY_PREFIX = "rate:limit:";

    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(String actionName, String clientIdentifier, int limit, int periodSeconds) {
        String key = RATE_LIMIT_KEY_PREFIX + actionName + ":" + clientIdentifier;
        long now = System.currentTimeMillis();
        long windowStart = now - (periodSeconds * 1000L);

        // 1. 현재 윈도우 범위 밖의 과거 타임스탬프 Score 일괄 삭제
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 2. 현재 유효 윈도우 내 요청 횟수 카운트
        Long currentCount = redisTemplate.opsForZSet().zCard(key);
        if (currentCount != null && currentCount >= limit) {
            log.warn("[Rate Limit 차단 (429)] key={}, 한도={}/{}초, 현재요청={}",
                    key, limit, periodSeconds, currentCount);
            return false;
        }

        // 3. 신규 요청 타임스탬프 기록 (Member: "타임스탬프:UUID", Score: 밀리초)
        String member = now + ":" + UUID.randomUUID().toString().substring(0, 8);
        redisTemplate.opsForZSet().add(key, member, now);
        
        // 메모리 누수 방지 (윈도우 시간의 2배 후 자동 만료)
        redisTemplate.expire(key, periodSeconds * 2L, TimeUnit.SECONDS);

        log.debug("[Rate Limit 통과] key={}, 요청수={}/{}", key, (currentCount == null ? 0 : currentCount) + 1, limit);
        return true;
    }
}