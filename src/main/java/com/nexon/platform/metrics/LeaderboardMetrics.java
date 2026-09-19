package com.nexon.platform.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class LeaderboardMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter scoreSubmitCounter;
    private final Counter topRankEventCounter;
    private final Timer scoreSubmitTimer;

    public LeaderboardMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 1. 누적 점수 등록 카운터
        this.scoreSubmitCounter = Counter.builder("leaderboard_score_submit_total")
                .description("누적 리더보드 점수 등록 성공 횟수")
                .register(meterRegistry);

        // 2. Top 10 진입 Kafka 이벤트 발행 카운터
        this.topRankEventCounter = Counter.builder("leaderboard_top_rank_event_total")
                .description("Top 10 진입 Kafka 랭킹 변동 이벤트 발행 횟수")
                .register(meterRegistry);

        // 3. 점수 등록 처리 소요 시간 타이머
        this.scoreSubmitTimer = Timer.builder("leaderboard_score_submit_duration_seconds")
                .description("리더보드 점수 등록 및 ZSET 적재 소요 시간")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    public void recordScoreSubmit(Runnable task) {
        scoreSubmitTimer.record(task);
        scoreSubmitCounter.increment();
    }

    public void incrementTopRankEvent() {
        topRankEventCounter.increment();
    }

    public void incrementRateLimitBlocked(String action) {
        meterRegistry.counter("ratelimit_blocked_total", "action", action).increment();
    }

    public void incrementIdempotencyBypassed(String action) {
        meterRegistry.counter("idempotency_bypassed_total", "action", action).increment();
    }
}