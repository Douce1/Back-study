package com.nexon.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexon.platform.dto.HallOfFameEntry;
import com.nexon.platform.dto.LeaderboardEntry;
import com.nexon.platform.dto.PageResponse;
import com.nexon.platform.dto.SeasonArchiveResponse;
import com.nexon.platform.dto.UserRankResponse;
import com.nexon.platform.entity.SeasonLeaderboardSnapshot;
import com.nexon.platform.repository.SeasonLeaderboardSnapshotRepository;
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

    // L1 로컬 캐시 (Caffeine: 최대 500개 페이지, 10분 만료)
    private final Cache<String, PageResponse<HallOfFameEntry>> localCache;

    public LeaderboardService(StringRedisTemplate redisTemplate,
                              SeasonLeaderboardSnapshotRepository snapshotRepository,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
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
        double compositeScore = calculateCompositeScore(score);
        redisTemplate.opsForZSet().add(DEFAULT_LEADERBOARD_KEY, String.valueOf(userId), compositeScore);
        log.info("[리더보드 점수 갱신] 유저 {}: 원본점수={}점 (복합점수={})", userId, score, compositeScore);
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

    // [Day 27] 과거 시즌 명예의 전당 멀티티어 캐시 조회 (L1 Caffeine -> L2 Redis -> DB)
    @Transactional(readOnly = true)
    public PageResponse<HallOfFameEntry> getHallOfFame(int seasonId, int page, int size) {
        int validatedSize = Math.min(Math.max(size, 1), 100);
        int validatedPage = Math.max(page, 0);

        String localCacheKey = String.format("hof:season:%d:page:%d:size:%d", seasonId, validatedPage, validatedSize);
        String redisCacheKey = "cache:" + localCacheKey;

        // 1. L1 로컬 캐시 (Caffeine) 확인 (0ms 참조)
        PageResponse<HallOfFameEntry> l1Cached = localCache.getIfPresent(localCacheKey);
        if (l1Cached != null) {
            log.info("[L1 로컬 캐시 적중 (Caffeine)] {}", localCacheKey);
            return l1Cached;
        }

        // 2. L2 분산 캐시 (Redis) 확인 (~1ms 참조)
        try {
            String l2CachedJson = redisTemplate.opsForValue().get(redisCacheKey);
            if (l2CachedJson != null) {
                log.info("[L2 분산 캐시 적중 (Redis)] {}", localCacheKey);
                PageResponse<HallOfFameEntry> l2Cached = objectMapper.readValue(
                        l2CachedJson,
                        new TypeReference<PageResponse<HallOfFameEntry>>() {}
                );
                // L1 로컬 캐시 동기화 (워밍)
                localCache.put(localCacheKey, l2Cached);
                return l2Cached;
            }
        } catch (Exception e) {
            log.warn("[L2 캐시 조회 실패 - DB 폴백 진행] error={}", e.getMessage());
        }

        // 3. 캐시 미스 -> RDBMS (MySQL) 인덱스 조회 (~10ms)
        log.info("[캐시 미스 -> RDBMS 조회 및 캐시 워밍] {}", localCacheKey);
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

        // 4. L1(Caffeine) 및 L2(Redis, TTL 1시간) 동시 적재
        localCache.put(localCacheKey, response);
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisCacheKey, json, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[L2 캐시 적재 실패] error={}", e.getMessage());
        }

        return response;
    }

    public void clearLocalCache() {
        localCache.invalidateAll();
    }

    public record ScoreData(Long userId, Double score) {}
}