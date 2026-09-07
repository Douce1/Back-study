package com.nexon.platform.scheduler;

import com.nexon.platform.entity.OutboxStatus;
import com.nexon.platform.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class OutboxCleaner {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleaner.class);
    private final OutboxRepository outboxRepository;

    public OutboxCleaner(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    // 1분(60,000ms)마다 실행되어 보관 주기 만료 데이터 정리
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void purgeOldPublishedEvents() {
        // 테스트 및 실습 검증을 위해 발행 완료 후 5분 경과 데이터를 삭제 (운영 환경: minusDays(7))
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);

        int deletedCount = outboxRepository.deleteByStatusAndPublishedAtBefore(OutboxStatus.PUBLISHED, threshold);

        if (deletedCount > 0) {
            log.info("[OutboxCleaner] 보관 기한 만료된 PUBLISHED 레코드 {}건 일괄 삭제(Purge) 완료", deletedCount);
        }
    }
}