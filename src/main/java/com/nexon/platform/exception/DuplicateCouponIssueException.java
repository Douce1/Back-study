package com.nexon.platform.exception;

public class DuplicateCouponIssueException extends RuntimeException {
    public DuplicateCouponIssueException(String message) {
        super(message);
    }
}