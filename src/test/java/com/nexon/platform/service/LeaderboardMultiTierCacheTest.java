package com.nexon.platform.service;

import com.nexon.platform.dto.HallOfFameEntry;
import com.nexon.platform.dto.PageResponse;
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
class LeaderboardMultiTierCacheTest {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private SeasonLeaderboardSnapshotRepository snapshotRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String REDIS_CACHE_KEY = "cache:hof:season:1:page:0:size:10";

    @BeforeEach
    void setUp() {
        snapshotRepository.deleteAll();
        redisTemplate.delete(REDIS_CACHE_KEY);
        leaderboardService.clearLocalCache();

        // 1위부터 10위까지 스냅샷 데이터 RDBMS 세팅
        for (int i = 1; i <= 10; i++) {
            snapshotRepository.save(new SeasonLeaderboardSnapshot(1, (long) i, i, 1000.0 - i));
        }
    }

    @Test
    @DisplayName("명예의 전당 조회 시 1회차는 DB 조회 및 캐시 적재, 2회차는 L1 로컬 캐시 적중, 3회차(L1 무효화)는 L2 Redis 캐시 적중이 일어나야 한다")
    void multiTierCache_StepByStep_Success() {
        // [Step 1] 1회차 호출: 캐시 미스 -> RDBMS 조회 후 L1, L2 적재
        PageResponse<HallOfFameEntry> step1Response = leaderboardService.getHallOfFame(1, 0, 10);
        assertThat(step1Response.content()).hasSize(10);
        assertThat(step1Response.content().get(0).rank()).isEqualTo(1);

        // Redis(L2)에 JSON 형태로 적재되었는지 직접 확인
        Boolean hasRedisKey = redisTemplate.hasKey(REDIS_CACHE_KEY);
        assertThat(hasRedisKey).isTrue();

        // [Step 2] 2회차 호출: L1 Caffeine 로컬 캐시 적중 (DB나 Redis 통신 없이 0ms 즉시 반환)
        PageResponse<HallOfFameEntry> step2Response = leaderboardService.getHallOfFame(1, 0, 10);
        assertThat(step2Response.content()).hasSize(10);
        assertThat(step2Response.content().get(0).score()).isEqualTo(step1Response.content().get(0).score());

        // [Step 3] L1 로컬 캐시 강제 무효화
        leaderboardService.clearLocalCache();

        // [Step 4] 3회차 호출: L1 미스 -> L2 Redis 분산 캐시 적중 (DB 조회 없음)
        PageResponse<HallOfFameEntry> step3Response = leaderboardService.getHallOfFame(1, 0, 10);
        assertThat(step3Response.content()).hasSize(10);
        assertThat(step3Response.content().get(0).userId()).isEqualTo(1L);
    }
}