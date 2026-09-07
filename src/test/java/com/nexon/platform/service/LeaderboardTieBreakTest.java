package com.nexon.platform.service;

import com.nexon.platform.dto.LeaderboardEntry;
import com.nexon.platform.dto.UserRankResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LeaderboardTieBreakTest {

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
    @DisplayName("동일 점수 인입 시 사전순이 아닌 선착순(먼저 등록한 유저)이 상위 순위를 차지해야 한다")
    void tieBreaking_FirstComeFirstServed_Success() throws InterruptedException {
        // Given: 유저 10번과 유저 2번 준비 (문자열 사전순 기준: "10" < "2" 이므로 기본 Redis라면 유저 2가 1위가 됨)
        Long firstUser = 10L;
        Long secondUser = 2L;
        Double equalScore = 10000.0;

        // When: 유저 10이 먼저 10000점 등록
        leaderboardService.submitScore(firstUser, equalScore);

        // 시간차 부여 (10ms)
        Thread.sleep(10);

        // 유저 2가 동일한 10000점 뒤늦게 등록
        leaderboardService.submitScore(secondUser, equalScore);

        // Then 1: Top 2 조회 시 선착순 유저 10이 1위, 유저 2가 2위여야 함
        List<LeaderboardEntry> topRankers = leaderboardService.getTopRankers(2);
        assertThat(topRankers).hasSize(2);

        assertThat(topRankers.get(0).rank()).isEqualTo(1);
        assertThat(topRankers.get(0).userId()).isEqualTo(firstUser);
        assertThat(topRankers.get(0).score()).isEqualTo(equalScore);

        assertThat(topRankers.get(1).rank()).isEqualTo(2);
        assertThat(topRankers.get(1).userId()).isEqualTo(secondUser);
        assertThat(topRankers.get(1).score()).isEqualTo(equalScore);

        // Then 2: 개별 랭킹 조회 검증
        UserRankResponse firstUserRank = leaderboardService.getUserRank(firstUser);
        UserRankResponse secondUserRank = leaderboardService.getUserRank(secondUser);

        assertThat(firstUserRank.rank()).isEqualTo(1);
        assertThat(secondUserRank.rank()).isEqualTo(2);
    }
}