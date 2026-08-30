package com.nexon.platform.service;

import com.nexon.platform.common.DistributedLock;
import com.nexon.platform.dto.CouponIssueEvent;
import com.nexon.platform.dto.CouponIssueResponse;
import com.nexon.platform.entity.PlatformCoupon;
import com.nexon.platform.exception.CouponOutOfStockException;
import com.nexon.platform.producer.CouponEventProducer;
import com.nexon.platform.repository.CouponRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponEventProducer eventProducer;
    private final StringRedisTemplate redisTemplate;

    public CouponService(
            CouponRepository couponRepository,
            CouponEventProducer eventProducer,
            StringRedisTemplate redisTemplate
    ) {
        this.couponRepository = couponRepository;
        this.eventProducer = eventProducer;
        this.redisTemplate = redisTemplate;
    }

    // 1. 락 없음
    @Transactional
    public CouponIssueResponse issueWithoutLock(Long couponId) {
        PlatformCoupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));
        coupon.decreaseRemain();
        return new CouponIssueResponse(coupon);
    }

    // 2. DB 비관적 락
    @Transactional
    public CouponIssueResponse issueWithDbLock(Long couponId) {
        PlatformCoupon coupon = couponRepository.findByIdWithPessimisticLock(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));
        coupon.decreaseRemain();
        return new CouponIssueResponse(coupon);
    }

    // 3. Redis 분산 락
    @DistributedLock(key = "'coupon:' + #couponId")
    public CouponIssueResponse issueWithRedisLock(Long couponId) {
        PlatformCoupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));
        coupon.decreaseRemain();
        return new CouponIssueResponse(coupon);
    }

    // 4. Kafka 비동기 이벤트 큐 완충 (대규모 트래픽 전용)
    public void issueWithKafkaQueue(Long couponId, Long userId) {
        String stockKey = "coupon:stock:" + couponId;

        // Redis 메모리 상에서 원자적(Atomic) 1 감소 (0.001초 소요)
        Long remain = redisTemplate.opsForValue().decrement(stockKey);

        if (remain == null || remain < 0) {
            // 품절 시 수량 원복 후 즉시 예외 반환
            redisTemplate.opsForValue().increment(stockKey);
            throw new CouponOutOfStockException("해당 쿠폰의 준비된 수량이 모두 소진되었습니다.");
        }

        // 재고 선점 성공 시 즉시 Kafka 이벤트 큐로 적재 (DB 쓰기 지연 완충)
        eventProducer.sendCouponIssueEvent(new CouponIssueEvent(couponId, userId));
    }
}