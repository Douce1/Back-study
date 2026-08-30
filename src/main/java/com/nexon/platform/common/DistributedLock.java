package com.nexon.platform.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락의 고유 이름 (SpEL 표현식 지원)
     * 예: key = "'coupon:' + #couponId"
     */
    String key();

    /**
     * 시간 단위 (기본: 초)
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 락 획득을 위해 대기할 최대 시간 (기본: 5초)
     */
    long waitTime() default 5L;

    /**
     * 락 점유 후 자동 해제될 시간 (기본: 3초)
     */
    long leaseTime() default 3L;
}