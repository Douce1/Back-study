package com.nexon.platform.repository;

import com.nexon.platform.entity.OutboxEvent;
import com.nexon.platform.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    // 발행 대기(PENDING) 상태인 이벤트를 생성 시점 오름차순으로 상위 50건 조회
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}