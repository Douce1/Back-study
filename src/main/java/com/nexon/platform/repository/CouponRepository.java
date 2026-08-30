package com.nexon.platform.repository;

import com.nexon.platform.entity.PlatformCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<PlatformCoupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PlatformCoupon c WHERE c.couponId = :couponId")
    Optional<PlatformCoupon> findByIdWithPessimisticLock(@Param("couponId") Long couponId);
}