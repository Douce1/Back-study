package com.nexon.platform.exception;

public class CouponOutOfStockException extends RuntimeException {
    public CouponOutOfStockException(String message) {
        super(message);
    }
}