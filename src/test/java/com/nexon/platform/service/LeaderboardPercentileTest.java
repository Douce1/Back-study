package com.nexon.platform.service;

import com.nexon.platform.dto.UserRankResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LeaderboardPercentileTest {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String LEADERBOARD_KEY = "leaderboard:season:1";

    @BeforeEach
    void setUp() {
        redisTemplate.delete(LEADERBOARD_KEY);
    }

    @Test
    @DisplayName("1,000명의 플레이어가 존재할 때 각 유저의 순위와 상위 백분위(Percentile)가 O(1)로 정확히 산출되어야 한다")
    void calculatePercentile_With1000Players_Success() {
        // Given: 1,000명의 유저 점수 생성 (userId 1 = 1,000점 ... userId 1,000 = 1점)
        int totalCount = 1000;
        List<LeaderboardService.ScoreData> bulkList = new ArrayList<>(totalCount);
        for (long i = 1; i <= totalCount; i++) {
            bulkList.add(new LeaderboardService.ScoreData(i, (double) (totalCount - i + 1)));
        }

        // When: 파이프라이닝으로 일괄 초고속 주입
        leaderboardService.bulkRegisterScores(bulkList);

        // Then 1: 1위 유저 (userId: 1, 점수: 1000점) -> 상위 0.1% (1 / 1000 * 100)
        UserRankResponse rank1 = leaderboardService.getUserRank(1L);
        assertThat(rank1.rank()).isEqualTo(1);
        assertThat(rank1.totalPlayers()).isEqualTo(1000L);
        assertThat(rank1.topPercentile()).isEqualTo(0.1);

        // Then 2: 100위 유저 (userId: 100, 점수: 901점) -> 상위 10.0% (100 / 1000 * 100)
        UserRankResponse rank100 = leaderboardService.getUserRank(100L);
        assertThat(rank100.rank()).isEqualTo(100);
        assertThat(rank100.topPercentile()).isEqualTo(10.0);

        // Then 3: 500위 유저 (userId: 500, 점수: 501점) -> 상위 50.0%
        UserRankResponse rank500 = leaderboardService.getUserRank(500L);
        assertThat(rank500.rank()).isEqualTo(500);
        assertThat(rank500.topPercentile()).isEqualTo(50.0);

        // Then 4: 꼴찌 유저 (userId: 1000, 점수: 1점) -> 상위 100.0%
        UserRankResponse rank1000 = leaderboardService.getUserRank(1000L);
        assertThat(rank1000.rank()).isEqualTo(1000);
        assertThat(rank1000.topPercentile()).isEqualTo(100.0);
    }
}