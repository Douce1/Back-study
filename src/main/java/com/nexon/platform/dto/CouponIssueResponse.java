package com.nexon.platform.dto;

import com.nexon.platform.entity.PlatformCoupon;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "쿠폰 발급 완료 응답 DTO")
public class CouponIssueResponse {

    @Schema(description = "쿠폰 고유 ID", example = "1")
    private Long couponId;

    @Schema(description = "쿠폰 이름", example = "메이플 한정판 훈장 쿠폰")
    private String couponName;

    @Schema(description = "발급 후 잔여 수량", example = "99")
    private int remainCount;

    public CouponIssueResponse() {
    }

    public CouponIssueResponse(PlatformCoupon coupon) {
        this.couponId = coupon.getCouponId();
        this.couponName = coupon.getCouponName();
        this.remainCount = coupon.getRemainCount();
    }

    public Long getCouponId() {
        return couponId;
    }

    public String getCouponName() {
        return couponName;
    }

    public int getRemainCount() {
        return remainCount;
    }
}