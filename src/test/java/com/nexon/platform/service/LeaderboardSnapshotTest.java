package com.nexon.platform.service;

import com.nexon.platform.dto.SeasonArchiveResponse;
import com.nexon.platform.entity.SeasonLeaderboardSnapshot;
import com.nexon.platform.repository.SeasonLeaderboardSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LeaderboardSnapshotTest {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private SeasonLeaderboardSnapshotRepository snapshotRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String LEADERBOARD_KEY = "leaderboard:season:1";

    @BeforeEach
    void setUp() {
        redisTemplate.delete(LEADERBOARD_KEY);
        snapshotRepository.deleteAll();
    }

    @Test
    @DisplayName("시즌 종료 시 Redis ZSET 순위 데이터가 MySQL 스냅샷 테이블로 일괄 이관되고 Redis 키가 초기화되어야 한다")
    void archiveSeason_Success() {
        // Given: 유저 1(100점), 유저 2(300점), 유저 3(200점) 등록
        leaderboardService.submitScore(1L, 100.0);
        leaderboardService.submitScore(2L, 300.0);
        leaderboardService.submitScore(3L, 200.0);

        // When: 시즌 1 아카이빙 실행
        SeasonArchiveResponse response = leaderboardService.archiveSeason(1);

        // Then 1: 응답 데이터 검증 (총 3명, 1위 유저 2번, 최고 점수 300점)
        assertThat(response.seasonId()).isEqualTo(1);
        assertThat(response.totalArchived()).isEqualTo(3);
        assertThat(response.topUserId()).isEqualTo(2L);
        assertThat(response.topScore()).isEqualTo(300.0);

        // Then 2: RDBMS(MySQL) 스냅샷 테이블 검증
        List<SeasonLeaderboardSnapshot> list = snapshotRepository.findBySeasonIdOrderByFinalRankAsc(1);
        assertThat(list).hasSize(3);

        // 1위: 유저 2 (300점)
        assertThat(list.get(0).getFinalRank()).isEqualTo(1);
        assertThat(list.get(0).getUserId()).isEqualTo(2L);
        assertThat(list.get(0).getFinalScore()).isEqualTo(300.0);

        // 2위: 유저 3 (200점)
        assertThat(list.get(1).getFinalRank()).isEqualTo(2);
        assertThat(list.get(1).getUserId()).isEqualTo(3L);
        assertThat(list.get(1).getFinalScore()).isEqualTo(200.0);

        // 3위: 유저 1 (100점)
        assertThat(list.get(2).getFinalRank()).isEqualTo(3);
        assertThat(list.get(2).getUserId()).isEqualTo(1L);
        assertThat(list.get(2).getFinalScore()).isEqualTo(100.0);

        // Then 3: Redis 키 삭제(시즌 초기화) 확인
        Boolean exists = redisTemplate.hasKey(LEADERBOARD_KEY);
        assertThat(exists).isFalse();
    }
}