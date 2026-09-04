package com.nexon.platform.service;

import com.nexon.platform.dto.CouponIssueEvent;
import com.nexon.platform.dto.CouponIssueResponse;
import com.nexon.platform.entity.PlatformCoupon;
import com.nexon.platform.exception.DuplicateCouponIssueException;
import com.nexon.platform.producer.CouponEventProducer;
import com.nexon.platform.repository.CouponRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final CouponEventProducer eventProducer;

    // Prometheus 실시간 모니터링 카운터
    private final Counter acceptedCounter;
    private final Counter soldoutCounter;
    private final Counter duplicateCounter;

    public CouponService(CouponRepository couponRepository,
                         RedissonClient redissonClient,
                         StringRedisTemplate redisTemplate,
                         CouponEventProducer eventProducer,
                         MeterRegistry meterRegistry) {
        this.couponRepository = couponRepository;
        this.redissonClient = redissonClient;
        this.redisTemplate = redisTemplate;
        this.eventProducer = eventProducer;

        // Prometheus 커스텀 메트릭 등록
        this.acceptedCounter = Counter.builder("coupon_issue_accepted_total")
                .description("쿠폰 발급 접수 성공 총 건수")
                .register(meterRegistry);

        this.soldoutCounter = Counter.builder("coupon_issue_soldout_total")
                .description("쿠폰 재고 소진(품절) 차단 총 건수")
                .register(meterRegistry);

        this.duplicateCounter = Counter.builder("coupon_issue_duplicate_total")
                .description("1인 1회 중복 발급 시도 차단 총 건수")
                .register(meterRegistry);
    }

    @Transactional
    public CouponIssueResponse issueWithoutLock(Long couponId) {
        PlatformCoupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 정보를 찾을 수 없습니다."));
        coupon.decreaseRemain();
        return new CouponIssueResponse(coupon);
    }

    @Transactional
    public CouponIssueResponse issueWithDbLock(Long couponId) {
        PlatformCoupon coupon = couponRepository.findByIdWithPessimisticLock(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 정보를 찾을 수 없습니다."));
        coupon.decreaseRemain();
        return new CouponIssueResponse(coupon);
    }

    public CouponIssueResponse issueWithRedisLock(Long couponId) {
        String lockKey = "lock:coupon:" + couponId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(3, 2, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new IllegalStateException("락 획득 실패 - 트래픽 초과");
            }
            return issueWithoutLock(couponId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void issueWithKafkaQueue(Long couponId, Long userId) {
        String issuedKey = "coupon:issued:" + couponId;
        String stockKey = "coupon:stock:" + couponId;

        // 1. 1인 1회 중복 발급 방지 (Redis Set SADD)
        Long isFirst = redisTemplate.opsForSet().add(issuedKey, String.valueOf(userId));
        if (isFirst == null || isFirst == 0L) {
            duplicateCounter.increment(); // 중복 차단 메트릭 증가
            throw new DuplicateCouponIssueException("이미 발급받은 쿠폰입니다. (1인 1회 한정)");
        }

        // 2. 인메모리 원자적 재고 선점 (DECR)
        Long remainStock = redisTemplate.opsForValue().decrement(stockKey);
        if (remainStock != null && remainStock < 0) {
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(issuedKey, String.valueOf(userId));
            soldoutCounter.increment(); // 품절 메트릭 증가
            throw new IllegalArgumentException("쿠폰 재고가 모두 소진되었습니다.");
        }

        // 3. Kafka 비동기 큐 이벤트 발행
        try {
            eventProducer.sendCouponIssueEvent(new CouponIssueEvent(couponId, userId));
            acceptedCounter.increment(); // 접수 성공 메트릭 증가
        } catch (Exception e) {
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(issuedKey, String.valueOf(userId));
            throw new RuntimeException("Kafka 큐 전송 실패로 인한 롤백", e);
        }
    }
}