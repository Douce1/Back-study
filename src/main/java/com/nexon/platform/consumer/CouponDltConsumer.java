package com.nexon.platform.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexon.platform.dto.CouponIssueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class CouponDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponDltConsumer.class);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public CouponDltConsumer(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(topics = "coupon-issue-topic.DLT", groupId = "coupon-dlt-group")
    public void consumeDeadLetter(
            String payload,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String errorMessage
    ) {
        log.error("==================================================================");
        log.error("[🚨 DLQ 격리 알림] 3회 재시도 모두 실패! 실패 메시지가 DLT로 이관되었습니다.");
        log.error("[🚨 DLQ 원인] 에러 내용: {}", errorMessage);
        log.error("[🚨 DLQ 본문] Payload: {}", payload);
        log.error("==================================================================");

        try {
            CouponIssueEvent event = objectMapper.readValue(payload, CouponIssueEvent.class);

            // [핵심] 보상 트랜잭션: 발급 실패 유저의 Redis 재고 및 1인 1회 발급 Set 롤백
            String stockKey = "coupon:stock:" + event.getCouponId();
            String issuedKey = "coupon:issued:" + event.getCouponId();

            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(issuedKey, String.valueOf(event.getUserId()));

            log.warn("[DLQ 보상 완료] 유저(userId: {})의 쿠폰(couponId: {}) Redis 선점 데이터가 원복되었습니다.",
                    event.getUserId(), event.getCouponId());
        } catch (Exception e) {
            log.error("[DLQ 치명적 오류] 보상 트랜잭션 수행 실패! 관리자 수동 개입 필요: {}", payload, e);
        }
    }
}