package com.nexon.platform.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexon.platform.dto.LeaderboardRankChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

@Component
public class LeaderboardEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardEventConsumer.class);
    public static final String LEADERBOARD_RANK_TOPIC = "leaderboard-rank-topic";

    private final ObjectMapper objectMapper;
    // 단위 테스트 실측 검증을 위한 인메모리 수신 버퍼
    private final List<LeaderboardRankChangeEvent> receivedEvents = new CopyOnWriteArrayList<>();

    public LeaderboardEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = LEADERBOARD_RANK_TOPIC, groupId = "leaderboard-notification-group")
    public void consumeRankChangeEvent(String message) {
        try {
            LeaderboardRankChangeEvent event = objectMapper.readValue(message, LeaderboardRankChangeEvent.class);
            receivedEvents.add(event);

            log.info("==================================================================");
            log.info("[Kafka 랭킹 이벤트 수신 / 월드 공지 브로드캐스트] 📢");
            log.info(">>> 유저 [{}]님이 실시간 랭킹 [{}]위(점수: {}점)에 등극했습니다!",
                    event.userId(), event.newRank(), event.score());
            log.info(">>> 이벤트 타입: {}, 타임스탬프: {}", event.eventType(), event.eventTimestamp());
            log.info("==================================================================");
        } catch (Exception e) {
            log.error("[Kafka 랭킹 이벤트 역직렬화 실패] message={}, error={}", message, e.getMessage());
        }
    }

    public List<LeaderboardRankChangeEvent> getReceivedEvents() {
        return receivedEvents;
    }

    public void clearReceivedEvents() {
        receivedEvents.clear();
    }
}