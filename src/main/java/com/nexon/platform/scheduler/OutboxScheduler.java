package com.nexon.platform.scheduler;

import com.nexon.platform.entity.OutboxEvent;
import com.nexon.platform.entity.OutboxStatus;
import com.nexon.platform.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);
    private static final String TOPIC = "coupon-issue-topic";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxScheduler(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 10000) // 10초 주기로 PENDING 이벤트 폴링
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("[OutboxScheduler] 발행 대기 이벤트 {}건 감지, 순차 Kafka 전송 시작", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // 동기 대기(.get)로 전송 성공 확인 후 영속성 컨텍스트 내에서 상태 즉시 변경
                kafkaTemplate.send(TOPIC, event.getPayload()).get(3, TimeUnit.SECONDS);
                event.markAsPublished();
                outboxRepository.save(event);
                log.info("[Outbox 완료] eventId: {} -> Kafka 발행 및 PUBLISHED 갱신 완료", event.getEventId());
            } catch (Exception e) {
                log.error("[Outbox 실패] eventId: {} 전송 중 오류 발생. 다음 주기에 재시도", event.getEventId(), e);
            }
        }
    }
}