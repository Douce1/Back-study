package com.nexon.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexon.platform.config.CachePubSubConfig;
import com.nexon.platform.consumer.LeaderboardEventConsumer;
import com.nexon.platform.dto.HallOfFameEntry;
import com.nexon.platform.dto.LeaderboardEntry;
import com.nexon.platform.dto.LeaderboardRankChangeEvent;
import com.nexon.platform.dto.PageResponse;
import com.nexon.platform.dto.SeasonArchiveResponse;
import com.nexon.platform.dto.UserRankResponse;
import com.nexon.platform.entity.SeasonLeaderboardSnapshot;
import com.nexon.platform.metrics.LeaderboardMetrics;
import com.nexon.platform.repository.SeasonLeaderboardSnapshotRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
    private static final String LEADERBOARD_KEY_PREFIX = "leaderboard:season:";
    private static final String DEFAULT_LEADERBOARD_KEY = "leaderboard:season:1";

    private static final double MAX_TIMESTAMP_MS = 2_000_000_000_000.0;
    private static final double SCALE_FACTOR = 10_000_000_000_000.0;

    private final StringRedisTemplate redisTemplate;
    private final SeasonLeaderboardSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LeaderboardMetrics leaderboardMetrics;

    // L1 로컬 캐시 (Caffeine: 최대 500개 페이지, 10분 만료)
    private final Cache<String, PageResponse<HallOfFameEntry>> localCache;

    public LeaderboardService(StringRedisTemplate redisTemplate,
                              SeasonLeaderboardSnapshotRepository snapshotRepository,
                              ObjectMapper objectMapper,
                              RedissonClient redissonClient,
                              KafkaTemplate<String, String> kafkaTemplate,
                              LeaderboardMetrics leaderboardMetrics) {
        this.redisTemplate = redisTemplate;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
        this.redissonClient = redissonClient;
        this.kafkaTemplate = kafkaTemplate;
        this.leaderboardMetrics = leaderboardMetrics;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }

    private double calculateCompositeScore(Double baseScore) {
        long currentMillis = System.currentTimeMillis();
        double tieBreaker = (MAX_TIMESTAMP_MS - currentMillis) / SCALE_FACTOR;
        return Math.floor(baseScore) + tieBreaker;
    }

    private double extractBaseScore(Double compositeScore) {
        return Math.floor(compositeScore);
    }

    public void submitScore(Long userId, Double score) {
        // 타이머 계측 및 점수 등록 카운트 증가
        leaderboardMetrics.recordScoreSubmit(() -> {
            double compositeScore = calculateCompositeScore(score);
            redisTemplate.opsForZSet().add(DEFAULT_LEADERBOARD_KEY, String.valueOf(userId), compositeScore);
            log.info("[리더보드 점수 갱신] 유저 {}: 원본점수={}점 (복합점수={})", userId, score, compositeScore);
        });

        // Top 10 진입 실시간 판별 및 Kafka 비동기 이벤트 스트리밍
        try {
            Long currentRankIndex = redisTemplate.opsForZSet().reverseRank(DEFAULT_LEADERBOARD_KEY, String.valueOf(userId));
            if (currentRankIndex != null && currentRankIndex < 10) { // 0~9위 (Top 10)
                int displayRank = currentRankIndex.intValue() + 1;
                LeaderboardRankChangeEvent event = LeaderboardRankChangeEvent.topRankEntry(userId, displayRank, score);
                String eventPayload = objectMapper.writeValueAsString(event);

                kafkaTemplate.send(LeaderboardEventConsumer.LEADERBOARD_RANK_TOPIC, String.valueOf(userId), eventPayload);
                leaderboardMetrics.incrementTopRankEvent();
                log.info("[Kafka 랭킹 변동 이벤트 발행 완료] topic={}, 유저={}, 등수={}위",
                        LeaderboardEventConsumer.LEADERBOARD_RANK_TOPIC, userId, displayRank);
            }
        } catch (Exception e) {
            log.error("[Kafka 랭킹 이벤트 발행 실패 - 격리 처리] userId={}, error={}", userId, e.getMessage());
        }
    }

    public List<LeaderboardEntry> getTopRankers(int limit) {
        Set<ZSetOperations.TypedTuple<String>> rankTuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(DEFAULT_LEADERBOARD_KEY, 0, limit - 1);

        List<LeaderboardEntry> result = new ArrayList<>();
        if (rankTuples == null || rankTuples.isEmpty()) {
            return result;
        }

        int currentRank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : rankTuples) {
            Long userId = Long.valueOf(tuple.getValue());
            Double originalScore = extractBaseScore(tuple.getScore());
            result.add(new LeaderboardEntry(currentRank++, userId, originalScore));
        }

        return result;
    }

    public UserRankResponse getUserRank(Long userId) {
        Long rankIndex = redisTemplate.opsForZSet().reverseRank(DEFAULT_LEADERBOARD_KEY, String.valueOf(userId));
        Double compositeScore = redisTemplate.opsForZSet().score(DEFAULT_LEADERBOARD_KEY, String.valueOf(userId));
        Long totalPlayers = redisTemplate.opsForZSet().size(DEFAULT_LEADERBOARD_KEY);

        if (rankIndex == null || compositeScore == null) {
            throw new IllegalArgumentException("리더보드에 등록되지 않은 유저입니다.");
        }

        int rank = rankIndex.intValue() + 1;
        long total = (totalPlayers != null && totalPlayers > 0) ? totalPlayers : 1L;

        double rawPercentile = ((double) rank / total) * 100.0;
        double roundedPercentile = Math.round(rawPercentile * 100.0) / 100.0;

        return new UserRankResponse(userId, rank, extractBaseScore(compositeScore), roundedPercentile, total);
    }

    public void bulkRegisterScores(List<ScoreData> scores) {
        byte[] keyBytes = DEFAULT_LEADERBOARD_KEY.getBytes(StandardCharsets.UTF_8);

        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (ScoreData data : scores) {
                    byte[] memberBytes = String.valueOf(data.userId()).getBytes(StandardCharsets.UTF_8);
                    double compositeScore = calculateCompositeScore(data.score());
                    connection.zSetCommands().zAdd(keyBytes, compositeScore, memberBytes);
                }
                return null;
            }
        });
        log.info("[Redis 파이프라인] 더미 유저 {}명의 점수가 초고속 일괄 적재되었습니다.", scores.size());
    }

    @Transactional
    public SeasonArchiveResponse archiveSeason(int seasonId) {
        String seasonKey = LEADERBOARD_KEY_PREFIX + seasonId;
        Long total = redisTemplate.opsForZSet().size(seasonKey);

        if (total == null || total == 0) {
            throw new IllegalStateException("시즌 " + seasonId + "에 아카이빙할 랭킹 데이터가 존재하지 않습니다.");
        }

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(seasonKey, 0, -1);

        if (tuples == null || tuples.isEmpty()) {
            throw new IllegalStateException("시즌 랭킹 데이터 추출 실패");
        }

        List<SeasonLeaderboardSnapshot> snapshots = new ArrayList<>();
        int rank = 1;
        Long topUserId = null;
        Double topScore = null;

        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long userId = Long.valueOf(tuple.getValue());
            Double baseScore = extractBaseScore(tuple.getScore());

            if (rank == 1) {
                topUserId = userId;
                topScore = baseScore;
            }
            snapshots.add(new SeasonLeaderboardSnapshot(seasonId, userId, rank++, baseScore));
        }

        snapshotRepository.saveAll(snapshots);
        redisTemplate.delete(seasonKey);

        log.info("[시즌 아카이빙 완료] 시즌 {}: 총 {}명 RDBMS 영속화 및 Redis 시즌 키 삭제 완료", seasonId, snapshots.size());
        return new SeasonArchiveResponse(seasonId, snapshots.size(), topUserId, topScore);
    }

    @Transactional(readOnly = true)
    public PageResponse<HallOfFameEntry> getHallOfFame(int seasonId, int page, int size) {
        int validatedSize = Math.min(Math.max(size, 1), 100);
        int validatedPage = Math.max(page, 0);

        String localCacheKey = String.format("hof:season:%d:page:%d:size:%d", seasonId, validatedPage, validatedSize);
        String redisCacheKey = "cache:" + localCacheKey;
        String lockKey = "lock:" + localCacheKey;

        // 1. L1 로컬 캐시 (Caffeine) 확인 (0ms)
        PageResponse<HallOfFameEntry> l1Cached = localCache.getIfPresent(localCacheKey);
        if (l1Cached != null) {
            log.info("[L1 로컬 캐시 적중 (Caffeine)] {}", localCacheKey);
            return l1Cached;
        }

        // 2. L2 분산 캐시 (Redis) 확인 (~1ms)
        PageResponse<HallOfFameEntry> l2Cached = getFromRedis(redisCacheKey, localCacheKey);
        if (l2Cached != null) {
            return l2Cached;
        }

        // 3. 캐시 미스 -> Redisson 분산 락(Mutex) 획득 시도
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean isLocked = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (!isLocked) {
                log.warn("[락 획득 타임아웃] {}", lockKey);
                PageResponse<HallOfFameEntry> retryL1 = localCache.getIfPresent(localCacheKey);
                if (retryL1 != null) return retryL1;
                PageResponse<HallOfFameEntry> retryL2 = getFromRedis(redisCacheKey, localCacheKey);
                if (retryL2 != null) return retryL2;
                throw new IllegalStateException("동시 요청 과다로 일시적 조회 지연이 발생했습니다.");
            }

            try {
                PageResponse<HallOfFameEntry> doubleCheckL1 = localCache.getIfPresent(localCacheKey);
                if (doubleCheckL1 != null) {
                    log.info("[Double-Check L1 적중] 락 대기 후 L1 캐시 재활용 {}", localCacheKey);
                    return doubleCheckL1;
                }
                PageResponse<HallOfFameEntry> doubleCheckL2 = getFromRedis(redisCacheKey, localCacheKey);
                if (doubleCheckL2 != null) {
                    log.info("[Double-Check L2 적중] 락 대기 후 L2 캐시 재활용 {}", localCacheKey);
                    return doubleCheckL2;
                }

                // 4. 단 1회 DB 조회 및 캐시 워밍
                log.info("[단 1회 DB 조회 및 캐시 워밍 진행] {}", localCacheKey);
                Pageable pageable = PageRequest.of(validatedPage, validatedSize);
                Page<SeasonLeaderboardSnapshot> snapshotPage =
                        snapshotRepository.findBySeasonIdOrderByFinalRankAsc(seasonId, pageable);

                Page<HallOfFameEntry> entryPage = snapshotPage.map(snapshot ->
                        new HallOfFameEntry(
                                snapshot.getFinalRank(),
                                snapshot.getUserId(),
                                snapshot.getFinalScore(),
                                snapshot.getArchivedAt()
                        )
                );

                PageResponse<HallOfFameEntry> response = PageResponse.from(entryPage);

                // L1 및 L2(TTL 1시간) 동시 적재
                localCache.put(localCacheKey, response);
                try {
                    String json = objectMapper.writeValueAsString(response);
                    redisTemplate.opsForValue().set(redisCacheKey, json, 1, TimeUnit.HOURS);
                } catch (Exception e) {
                    log.warn("[L2 캐시 적재 실패] error={}", e.getMessage());
                }

                return response;
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("분산 락 대기 중 인터럽트 발생", e);
        }
    }

    private PageResponse<HallOfFameEntry> getFromRedis(String redisCacheKey, String localCacheKey) {
        try {
            String l2CachedJson = redisTemplate.opsForValue().get(redisCacheKey);
            if (l2CachedJson != null) {
                log.info("[L2 분산 캐시 적중 (Redis)] {}", localCacheKey);
                PageResponse<HallOfFameEntry> l2Cached = objectMapper.readValue(
                        l2CachedJson,
                        new TypeReference<PageResponse<HallOfFameEntry>>() {}
                );
                localCache.put(localCacheKey, l2Cached);
                return l2Cached;
            }
        } catch (Exception e) {
            log.warn("[L2 캐시 조회 실패 - DB 폴백] error={}", e.getMessage());
        }
        return null;
    }

    // 분산 캐시 무효화 트리거: L1 삭제 -> L2(Redis) 삭제 -> 전체 인스턴스 Pub/Sub 브로드캐스트
    public void evictHallOfFameCache(int seasonId, int page, int size) {
        String localCacheKey = String.format("hof:season:%d:page:%d:size:%d", seasonId, page, size);
        String redisCacheKey = "cache:" + localCacheKey;

        localCache.invalidate(localCacheKey);
        redisTemplate.delete(redisCacheKey);
        redisTemplate.convertAndSend(CachePubSubConfig.CACHE_EVICT_TOPIC, localCacheKey);
        log.info("[분산 캐시 무효화 완료 및 Pub/Sub 브로드캐스트] targetKey={}", localCacheKey);
    }

    // Redis Pub/Sub 채널로부터 무효화 메시지 수신 시 실행되는 리스너 콜백
    public void handleCacheEvictMessage(String message) {
        log.info("[L1 로컬 캐시 분산 무효화 이벤트 수신] 대상 키={}", message);
        if ("ALL".equalsIgnoreCase(message.trim())) {
            localCache.invalidateAll();
        } else {
            localCache.invalidate(message.trim());
        }
    }

    public boolean isLocalCached(int seasonId, int page, int size) {
        String localCacheKey = String.format("hof:season:%d:page:%d:size:%d", seasonId, page, size);
        return localCache.getIfPresent(localCacheKey) != null;
    }

    public void clearLocalCache() {
        localCache.invalidateAll();
    }

    public record ScoreData(Long userId, Double score) {}
}