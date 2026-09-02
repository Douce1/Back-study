package com.nexon.platform.service;

import com.nexon.platform.common.DistributedLock;
import com.nexon.platform.dto.CouponIssueEvent;
import com.nexon.platform.dto.CouponIssueResponse;
import com.nexon.platform.entity.PlatformCoupon;
import com.nexon.platform.exception.CouponOutOfStockException;
import com.nexon.platform.exception.DuplicateCouponIssueException;
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

    // 4. Kafka 비동기 이벤트 큐 완충 + 1인 1쿠폰 중복 방지 (Redis Set SADD)
    public void issueWithKafkaQueue(Long couponId, Long userId) {
        String issuedKey = "coupon:issued:" + couponId;
        String stockKey = "coupon:stock:" + couponId;

        // [1] Redis Set(SADD)을 이용한 1인 1쿠폰 원자적 중복 검증 (0.001초 소요)
        Long isNewUser = redisTemplate.opsForSet().add(issuedKey, String.valueOf(userId));
        if (isNewUser == null || isNewUser == 0) {
            throw new DuplicateCouponIssueException("이미 발급받은 쿠폰입니다. (1인 1회 한정)");
        }

        // [2] Redis 총 재고 원자적 1 차감 (DECR)
        Long remain = redisTemplate.opsForValue().decrement(stockKey);

        if (remain == null || remain < 0) {
            // 품절 시 재고 원복 및 Set에서 유저 등록 롤백
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(issuedKey, String.valueOf(userId));
            throw new CouponOutOfStockException("해당 쿠폰의 준비된 수량이 모두 소진되었습니다.");
        }

        // [3] 재고 선점 성공 시 Kafka 이벤트 발행 (발행 실패 시 롤백)
        try {
            eventProducer.sendCouponIssueEvent(new CouponIssueEvent(couponId, userId));
        } catch (Exception e) {
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(issuedKey, String.valueOf(userId));
            throw e;
        }
    }
}