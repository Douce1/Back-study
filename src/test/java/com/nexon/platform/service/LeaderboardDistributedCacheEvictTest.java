package com.nexon.platform.service;

import com.nexon.platform.entity.SeasonLeaderboardSnapshot;
import com.nexon.platform.repository.SeasonLeaderboardSnapshotRepository;
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
class LeaderboardDistributedCacheEvictTest {

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

        for (int i = 1; i <= 10; i++) {
            snapshotRepository.save(new SeasonLeaderboardSnapshot(1, (long) i, i, 1000.0 - i));
        }
    }

    @Test
    @DisplayName("캐시 무효화 호출 시 L2 Redis 키가 삭제되고 Redis Pub/Sub을 통해 L1 Caffeine 로컬 캐시도 즉시 무효화되어야 한다")
    void evictHallOfFameCache_DistributedSync_Success() {
        // [Step 1] 최초 조회 수행 -> L1 및 L2에 캐시 적재 완료
        leaderboardService.getHallOfFame(1, 0, 10);
        assertThat(leaderboardService.isLocalCached(1, 0, 10)).isTrue();
        assertThat(redisTemplate.hasKey(REDIS_CACHE_KEY)).isTrue();

        // [Step 2] 분산 캐시 무효화 트리거 실행 (L1 삭제 + L2 삭제 + Pub/Sub 브로드캐스트)
        leaderboardService.evictHallOfFameCache(1, 0, 10);

        // L2 Redis 키가 즉각 삭제되었는지 확인
        assertThat(redisTemplate.hasKey(REDIS_CACHE_KEY)).isFalse();

        // [Step 3] Redis Pub/Sub 메시지 수신 후 L1 Caffeine이 무효화되었는지 비동기 대기 검증 (최대 3초)
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(leaderboardService.isLocalCached(1, 0, 10)).isFalse();
        });
    }
}