package com.nexon.platform.consumer;

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

    public CouponEventConsumer(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @KafkaListener(topics = "coupon-issue-topic", groupId = "coupon-issue-group")
    @Transactional
    public void consumeCouponIssue(CouponIssueEvent event) {
        log.info("[KafkaConsumer] 백그라운드 DB 반영 시작 - couponId: {}, userId: {}", event.getCouponId(), event.getUserId());

        PlatformCoupon coupon = couponRepository.findById(event.getCouponId())
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 정보를 찾을 수 없습니다."));

        coupon.decreaseRemain();
        log.info("[KafkaConsumer] DB 재고 차감 완료 - 남은 수량: {}", coupon.getRemainCount());
    }
}