package com.nexon.platform.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexon.platform.dto.CouponIssueEvent;
import com.nexon.platform.entity.PlatformCoupon;
import com.nexon.platform.repository.CouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CouponEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponEventConsumer.class);

    private final CouponRepository couponRepository;
    private final ObjectMapper objectMapper;

    public CouponEventConsumer(CouponRepository couponRepository, ObjectMapper objectMapper) {
        this.couponRepository = couponRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "coupon-issue-topic", groupId = "coupon-issue-group")
    @Transactional
    public void consumeCouponIssue(String messagePayload) {
        try {
            CouponIssueEvent event = objectMapper.readValue(messagePayload, CouponIssueEvent.class);
            log.info("[KafkaConsumer] 백그라운드 DB 반영 시작 - couponId: {}, userId: {}", event.getCouponId(), event.getUserId());

            // [테스트 시뮬레이션] 999번 쿠폰 요청 시 인위적인 DB 타임아웃/데드락 장애 유발
            if (event.getCouponId() == 999L) {
                log.warn("[⚠️ 모의 장애 발생] 쿠폰 ID가 999이므로 DB Connection Timeout 예외를 강제 발생시킵니다.");
                throw new RuntimeException("DB Connection Timeout 발생! (Simulated Error)");
            }

            PlatformCoupon coupon = couponRepository.findById(event.getCouponId())
                    .orElseThrow(() -> new IllegalArgumentException("쿠폰 정보를 찾을 수 없습니다."));

            coupon.decreaseRemain();
            log.info("[KafkaConsumer] DB 재고 차감 완료 - 남은 수량: {}", coupon.getRemainCount());
        } catch (JsonProcessingException e) {
            log.error("[KafkaConsumer] 메시지 역직렬화 실패: {}", messagePayload, e);
            throw new RuntimeException("역직렬화 실패", e);
        }
    }
}