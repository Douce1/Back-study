package com.nexon.platform.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexon.platform.dto.CouponIssueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CouponEventProducer {

    private static final Logger log = LoggerFactory.getLogger(CouponEventProducer.class);
    private static final String TOPIC_NAME = "coupon-issue-topic";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CouponEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendCouponIssueEvent(CouponIssueEvent event) {
        log.info("[KafkaProducer] 쿠폰 발급 이벤트 큐 적재 - couponId: {}, userId: {}", event.getCouponId(), event.getUserId());
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC_NAME, String.valueOf(event.getCouponId()), jsonPayload);
        } catch (JsonProcessingException e) {
            log.error("[KafkaProducer] 이벤트 직렬화 실패", e);
            throw new RuntimeException("쿠폰 이벤트 직렬화에 실패했습니다.", e);
        }
    }
}