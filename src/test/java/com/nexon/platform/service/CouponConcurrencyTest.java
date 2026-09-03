package com.nexon.platform.service;

import com.nexon.platform.entity.PlatformCoupon;
import com.nexon.platform.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class CouponConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(CouponConcurrencyTest.class);

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long testCouponId;

    @BeforeEach
    void setUp() {
        // 1. 테스트용 쿠폰 등록 (총 100개)
        PlatformCoupon coupon = couponRepository.save(
                new PlatformCoupon("메이플 넥슨캐시 1만원권", 100)
        );
        testCouponId = coupon.getCouponId();

        // 2. Redis 캐시 및 중복 발급 Set 초기화
        redisTemplate.opsForValue().set("coupon:stock:" + testCouponId, "100");
        redisTemplate.delete("coupon:issued:" + testCouponId);
    }

    @Test
    @DisplayName("[취약점 실측] 동시성 제어가 없는 경우: 1,000명 동시 요청 시 100개 초과 발급(Race Condition) 발생")
    void concurrencyTest_NoLock() throws InterruptedException {
        int threadCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    couponService.issueWithoutLock(testCouponId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long duration = System.currentTimeMillis() - startTime;

        PlatformCoupon result = couponRepository.findById(testCouponId).orElseThrow();

        log.info("==================================================================");
        log.info("[No-Lock 결과] 총 실행 시간: {}ms", duration);
        log.info("[No-Lock 결과] 발급 성공 건수: {}건 (정상 한도: 100건)", successCount.get());
        log.info("[No-Lock 결과] 발급 실패 건수: {}건", failCount.get());
        log.info("[No-Lock 결과] DB 최종 남은 재고: {}", result.getRemainCount());
        log.info("==================================================================");

        // [핵심 검증] 락이 없으면 100개 한정 수량임에도 100명을 초과하여 발급되는 심각한 결함 증명
        assertThat(successCount.get()).isGreaterThan(100);
    }

    @Test
    @DisplayName("[완전 무결성] Kafka 비동기 큐: 1,000명 동시 요청 시 정확히 100명만 선점 성공")
    void concurrencyTest_KafkaQueue() throws InterruptedException {
        int threadCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger acceptedCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();

        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= threadCount; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    couponService.issueWithKafkaQueue(testCouponId, userId);
                    acceptedCount.incrementAndGet();
                } catch (Exception e) {
                    rejectedCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long duration = System.currentTimeMillis() - startTime;

        String finalRedisStock = redisTemplate.opsForValue().get("coupon:stock:" + testCouponId);
        Long issuedUserCount = redisTemplate.opsForSet().size("coupon:issued:" + testCouponId);

        log.info("==================================================================");
        log.info("[Kafka 비동기 큐 결과] 인메모리 선점 소요 시간: {}ms", duration);
        log.info("[Kafka 비동기 큐 결과] 발급 접수 성공 유저 수: {}명 (정원: 100명)", acceptedCount.get());
        log.info("[Kafka 비동기 큐 결과] 품절 차단 유저 수: {}명", rejectedCount.get());
        log.info("[Kafka 비동기 큐 결과] Redis 잔여 재고: {}", finalRedisStock);
        log.info("[Kafka 비동기 큐 결과] Redis 발급 유저 Set 크기: {}", issuedUserCount);
        log.info("==================================================================");

        // 정확히 100명만 접수되고, 900명은 즉시 차단되었는지 정합성 검증
        assertThat(acceptedCount.get()).isEqualTo(100);
        assertThat(rejectedCount.get()).isEqualTo(900);
        assertThat(finalRedisStock).isEqualTo("0");
        assertThat(issuedUserCount).isEqualTo(100L);
    }
}