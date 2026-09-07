package com.nexon.platform.repository;

import com.nexon.platform.entity.OutboxEvent;
import com.nexon.platform.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    // 발행 대기(PENDING) 상태인 이벤트를 생성 시점 오름차순으로 상위 50건 조회
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    // 발행 완료(PUBLISHED)된 지 특정 시간이 지난 레코드를 단일 벌크 쿼리로 즉시 삭제
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM OutboxEvent o WHERE o.status = :status AND o.publishedAt < :threshold")
    int deleteByStatusAndPublishedAtBefore(@Param("status") OutboxStatus status, @Param("threshold") LocalDateTime threshold);
}