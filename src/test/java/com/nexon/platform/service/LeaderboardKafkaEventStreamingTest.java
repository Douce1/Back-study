package com.nexon.platform.service;

import com.nexon.platform.consumer.LeaderboardEventConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class LeaderboardKafkaEventStreamingTest {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private LeaderboardEventConsumer leaderboardEventConsumer;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String DEFAULT_LEADERBOARD_KEY = "leaderboard:season:1";

    @BeforeEach
    void setUp() {
        redisTemplate.delete(DEFAULT_LEADERBOARD_KEY);
        leaderboardEventConsumer.clearReceivedEvents();
    }

    @Test
    @DisplayName("유저 점수 등록 시 Top 10에 진입하면 Kafka로 이벤트가 스트리밍되어 컨슈머가 정상 수신해야 한다")
    void submitScore_ProducesKafkaEvent_WhenTop10Entry() {
        Long topRankerUserId = 777L;
        Double highGameScore = 95000.0;

        // 1. 점수 등록 실행 (상위 랭커 진입)
        leaderboardService.submitScore(topRankerUserId, highGameScore);

        // 2. Kafka 비동기 수신 대기 및 실측 검증 (최대 5초)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(leaderboardEventConsumer.getReceivedEvents()).isNotEmpty();
            var received = leaderboardEventConsumer.getReceivedEvents().get(0);
            assertThat(received.userId()).isEqualTo(topRankerUserId);
            assertThat(received.newRank()).isEqualTo(1);
            assertThat(received.score()).isEqualTo(highGameScore);
            assertThat(received.eventType()).isEqualTo("TOP_RANK_ENTRY");
        });
    }
}