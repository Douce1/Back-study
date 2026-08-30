package com.nexon.platform.producer;

import com.nexon.platform.dto.CouponIssueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CouponEventProducer {

    private static final Logger log = LoggerFactory.getLogger(CouponEventProducer.class);
    private static final String TOPIC_NAME = "coupon-issue-topic";

    private final KafkaTemplate<String, CouponIssueEvent> kafkaTemplate;

    public CouponEventProducer(KafkaTemplate<String, CouponIssueEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendCouponIssueEvent(CouponIssueEvent event) {
        log.info("[KafkaProducer] 쿠폰 발급 이벤트 큐 적재 - couponId: {}, userId: {}", event.getCouponId(), event.getUserId());
        kafkaTemplate.send(TOPIC_NAME, String.valueOf(event.getCouponId()), event);
    }
}