package com.nexon.platform.controller;

import com.nexon.platform.dto.CommonResponse;
import com.nexon.platform.dto.CouponIssueResponse;
import com.nexon.platform.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "02. Coupon Issue API", description = "초고속 대규모 쿠폰 발급 및 동시성 제어 API")
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @Operation(summary = "동시성 제어 없는 발급 (취약점 테스트용)")
    @PostMapping("/{couponId}/issue/no-lock")
    public CommonResponse<CouponIssueResponse> issueWithoutLock(@PathVariable("couponId") Long couponId) {
        CouponIssueResponse response = couponService.issueWithoutLock(couponId);
        return CommonResponse.ok("쿠폰 발급 요청 완료 (No Lock)", response);
    }

    @Operation(summary = "DB 비관적 락 발급 (Pessimistic Lock)")
    @PostMapping("/{couponId}/issue/db-lock")
    public CommonResponse<CouponIssueResponse> issueWithDbLock(@PathVariable("couponId") Long couponId) {
        CouponIssueResponse response = couponService.issueWithDbLock(couponId);
        return CommonResponse.ok("쿠폰 발급 요청 완료 (DB Lock)", response);
    }

    @Operation(summary = "Redis Redisson 분산 락 발급 (Distributed Lock)")
    @PostMapping("/{couponId}/issue/redis-lock")
    public CommonResponse<CouponIssueResponse> issueWithRedisLock(@PathVariable("couponId") Long couponId) {
        CouponIssueResponse response = couponService.issueWithRedisLock(couponId);
        return CommonResponse.ok("쿠폰 발급 요청 완료 (Redis Lock)", response);
    }

    @Operation(summary = "Kafka 비동기 큐 + 1인 1회 중복 발급 방지 (JWT 인증 필수)")
    @PostMapping("/{couponId}/issue/kafka")
    public CommonResponse<Void> issueWithKafkaQueue(@PathVariable("couponId") Long couponId,
                                                    @AuthenticationPrincipal Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요한 서비스입니다.");
        }
        couponService.issueWithKafkaQueue(couponId, userId);
        return CommonResponse.ok("쿠폰 발급 접수 완료 (Kafka 비동기 큐 & 1인 1회 한정)", null);
    }

    @Operation(summary = "Transactional Outbox 기반 쿠폰 발급 (메시지 유실 0% 보장)")
    @PostMapping("/{couponId}/issue/outbox")
    public CommonResponse<Void> issueCouponWithOutbox(@PathVariable("couponId") Long couponId,
                                                      @AuthenticationPrincipal Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요한 서비스입니다.");
        }
        couponService.issueWithOutboxPattern(couponId, userId);
        return CommonResponse.ok("쿠폰 발급 요청이 Outbox 테이블에 안전하게 접수되었습니다.", null);
    }
}