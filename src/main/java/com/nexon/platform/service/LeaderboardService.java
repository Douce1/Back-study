package com.nexon.platform.service;

import com.nexon.platform.dto.LeaderboardEntry;
import com.nexon.platform.dto.UserRankResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
    private static final String LEADERBOARD_KEY = "leaderboard:season:1";

    private static final double MAX_TIMESTAMP_MS = 2_000_000_000_000.0;
    private static final double SCALE_FACTOR = 10_000_000_000_000.0;

    private final StringRedisTemplate redisTemplate;

    public LeaderboardService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
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
        redisTemplate.opsForZSet().add(LEADERBOARD_KEY, String.valueOf(userId), compositeScore);
        log.info("[리더보드 점수 갱신] 유저 {}: 원본점수={}점 (복합점수={})", userId, score, compositeScore);
    }

    public List<LeaderboardEntry> getTopRankers(int limit) {
        Set<ZSetOperations.TypedTuple<String>> rankTuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(LEADERBOARD_KEY, 0, limit - 1);

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

    // 내 실시간 순위, 점수 및 O(1) 상위 백분위 계산
    public UserRankResponse getUserRank(Long userId) {
        Long rankIndex = redisTemplate.opsForZSet().reverseRank(LEADERBOARD_KEY, String.valueOf(userId));
        Double compositeScore = redisTemplate.opsForZSet().score(LEADERBOARD_KEY, String.valueOf(userId));
        Long totalPlayers = redisTemplate.opsForZSet().size(LEADERBOARD_KEY); // ZCARD: O(1)

        if (rankIndex == null || compositeScore == null) {
            throw new IllegalArgumentException("리더보드에 등록되지 않은 유저입니다.");
        }

        int rank = rankIndex.intValue() + 1;
        long total = (totalPlayers != null && totalPlayers > 0) ? totalPlayers : 1L;

        // 상위 백분위 계산 (소수점 둘째 자리까지 반올림: (rank / total) * 100)
        double rawPercentile = ((double) rank / total) * 100.0;
        double roundedPercentile = Math.round(rawPercentile * 100.0) / 100.0;

        return new UserRankResponse(userId, rank, extractBaseScore(compositeScore), roundedPercentile, total);
    }

    // Redis 파이프라이닝(Pipelining)을 통한 대규모 더미 점수 일괄 고속 적재
    public void bulkRegisterScores(List<ScoreData> scores) {
        byte[] keyBytes = LEADERBOARD_KEY.getBytes(StandardCharsets.UTF_8);

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

    public record ScoreData(Long userId, Double score) {}
}