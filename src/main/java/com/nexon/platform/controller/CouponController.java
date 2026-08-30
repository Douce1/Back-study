package com.nexon.platform.controller;

import com.nexon.platform.dto.CommonResponse;
import com.nexon.platform.dto.CouponIssueResponse;
import com.nexon.platform.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "02. Coupon Issue API", description = "동시성 제어 및 비동기 대기열 기반 선착순 쿠폰 발급 API")
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @Operation(summary = "1. 락 미적용 쿠폰 발급 (No-Lock)", description = "동시성 제어가 없어 Race Condition 및 초과 발급(데이터 오염)이 발생하는 엔드포인트")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공"),
            @ApiResponse(responseCode = "400", description = "수량 소진")
    })
    @PostMapping("/{couponId}/issue/no-lock")
    public CommonResponse<CouponIssueResponse> issueWithoutLock(
            @Parameter(description = "쿠폰 ID", example = "1") @PathVariable("couponId") Long couponId
    ) {
        return CommonResponse.ok("쿠폰 발급 완료(No-Lock)", couponService.issueWithoutLock(couponId));
    }

    @Operation(summary = "2. DB 비관적 락 쿠폰 발급 (Pessimistic Lock)", description = "SELECT ... FOR UPDATE를 사용하여 정합성을 보장하나 DB 커넥션 병목이 발생하는 엔드포인트")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공"),
            @ApiResponse(responseCode = "400", description = "수량 소진")
    })
    @PostMapping("/{couponId}/issue/db-lock")
    public CommonResponse<CouponIssueResponse> issueWithDbLock(
            @Parameter(description = "쿠폰 ID", example = "1") @PathVariable("couponId") Long couponId
    ) {
        return CommonResponse.ok("쿠폰 발급 완료(DB-Lock)", couponService.issueWithDbLock(couponId));
    }

    @Operation(summary = "3. Redis 분산 락 쿠폰 발급 (Redisson Lock)", description = "AOP 기반 @DistributedLock을 통해 인메모리에서 안전하게 락을 획득하고 DB 트랜잭션을 실행하는 엔드포인트")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공"),
            @ApiResponse(responseCode = "400", description = "수량 소진"),
            @ApiResponse(responseCode = "500", description = "락 획득 타임아웃")
    })
    @PostMapping("/{couponId}/issue/redis-lock")
    public CommonResponse<CouponIssueResponse> issueWithRedisLock(
            @Parameter(description = "쿠폰 ID", example = "1") @PathVariable("couponId") Long couponId
    ) {
        return CommonResponse.ok("쿠폰 발급 완료(Redis-Lock)", couponService.issueWithRedisLock(couponId));
    }

    @Operation(summary = "4. Kafka 비동기 이벤트 큐 쿠폰 발급 (대규모 트래픽 전용)", description = "Redis Atomic DECR로 0.001초 만에 재고를 선점하고 Kafka 큐에 적재하여 지연 없이 즉시 응답하는 완충 엔드포인트")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "비동기 발급 큐 접수 완료"),
            @ApiResponse(responseCode = "400", description = "수량 소진")
    })
    @PostMapping("/{couponId}/issue/kafka-async")
    public CommonResponse<String> issueWithKafka(
            @Parameter(description = "쿠폰 ID", example = "1") @PathVariable("couponId") Long couponId,
            @Parameter(description = "유저 ID", example = "1") @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        couponService.issueWithKafkaQueue(couponId, userId);
        return CommonResponse.ok("쿠폰 발급 요청이 접수되었습니다. (비동기 처리 대기열 등록)", "QUEUE_ACCEPTED");
    }
}