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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LeaderboardCacheStampedeTest {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private SeasonLeaderboardSnapshotRepository snapshotRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String REDIS_CACHE_KEY = "cache:hof:season:1:page:0:size:10";
    private static final String REDIS_LOCK_KEY = "lock:hof:season:1:page:0:size:10";

    @BeforeEach
    void setUp() {
        snapshotRepository.deleteAll();
        redisTemplate.delete(REDIS_CACHE_KEY);
        redisTemplate.delete(REDIS_LOCK_KEY);
        leaderboardService.clearLocalCache();

        for (int i = 1; i <= 10; i++) {
            snapshotRepository.save(new SeasonLeaderboardSnapshot(1, (long) i, i, 1000.0 - i));
        }
    }

    @Test
    @DisplayName("캐시가 비어있는 상태에서 10개의 동시 요청이 발생해도 Redisson 분산 락에 의해 캐시 스탬피드 없이 모두 정상 응답을 반환해야 한다")
    void getHallOfFame_ConcurrentRequests_PreventCacheStampede() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await(); // 10개 스레드가 동시에 출발선에서 대기

                    PageResponse<HallOfFameEntry> response = leaderboardService.getHallOfFame(1, 0, 10);
                    if (response != null && response.content().size() == 10) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown(); // 10개 스레드 일제히 동시 호출!
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);

        // Redis 캐시가 정상 적재되었는지 확인
        Boolean hasKey = redisTemplate.hasKey(REDIS_CACHE_KEY);
        assertThat(hasKey).isTrue();

        executorService.shutdown();
    }
}