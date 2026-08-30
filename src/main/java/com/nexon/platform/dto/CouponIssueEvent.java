package com.nexon.platform.dto;

import java.time.LocalDateTime;

public class CouponIssueEvent {

    private Long couponId;
    private Long userId;
    private LocalDateTime requestedAt;

    public CouponIssueEvent() {
    }

    public CouponIssueEvent(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
        this.requestedAt = LocalDateTime.now();
    }

    public Long getCouponId() {
        return couponId;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }
}