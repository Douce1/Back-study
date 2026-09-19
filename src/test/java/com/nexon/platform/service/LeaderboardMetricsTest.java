package com.nexon.platform.service;

import com.nexon.platform.metrics.LeaderboardMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LeaderboardMetricsTest {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private LeaderboardMetrics leaderboardMetrics;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("점수 등록 실행 시 점수 등록 카운터 및 소요 시간 타이머가 누적되어야 한다")
    void recordScoreSubmit_IncrementsMetrics() {
        double beforeCount = meterRegistry.get("leaderboard_score_submit_total").counter().count();

        leaderboardService.submitScore(9001L, 7500.0);

        double afterCount = meterRegistry.get("leaderboard_score_submit_total").counter().count();
        assertThat(afterCount).isEqualTo(beforeCount + 1.0);

        double timerCount = meterRegistry.get("leaderboard_score_submit_duration_seconds").timer().count();
        assertThat(timerCount).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("Rate Limit 차단 및 멱등성 바이패스 메트릭이 정상 카운팅되어야 한다")
    void customCounters_IncrementSuccessfully() {
        leaderboardMetrics.incrementRateLimitBlocked("test-action");
        double blockedCount = meterRegistry.get("ratelimit_blocked_total")
                .tag("action", "test-action")
                .counter().count();
        assertThat(blockedCount).isGreaterThanOrEqualTo(1.0);

        leaderboardMetrics.incrementIdempotencyBypassed("test-action");
        double bypassedCount = meterRegistry.get("idempotency_bypassed_total")
                .tag("action", "test-action")
                .counter().count();
        assertThat(bypassedCount).isGreaterThanOrEqualTo(1.0);
    }
}