package com.nexon.platform.service;

import com.nexon.platform.dto.LeaderboardEntry;
import com.nexon.platform.dto.UserRankResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
    private static final String LEADERBOARD_KEY = "leaderboard:season:1";

    // 약 2033년 시점 기준 에포크 밀리초 (타임스탬프 역산 상한치)
    private static final double MAX_TIMESTAMP_MS = 2_000_000_000_000.0;
    private static final double SCALE_FACTOR = 10_000_000_000_000.0;

    private final StringRedisTemplate redisTemplate;

    public LeaderboardService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 선착순 보정 복합 점수(Composite Score) 산출
    private double calculateCompositeScore(Double baseScore) {
        long currentMillis = System.currentTimeMillis();
        double tieBreaker = (MAX_TIMESTAMP_MS - currentMillis) / SCALE_FACTOR;
        return Math.floor(baseScore) + tieBreaker;
    }

    // 소수점 보정값을 제거한 원본 게임 점수 복원
    private double extractBaseScore(Double compositeScore) {
        return Math.floor(compositeScore);
    }

    // 점수 등록 및 실시간 선착순 랭킹 반영 (O(log N))
    public void submitScore(Long userId, Double score) {
        double compositeScore = calculateCompositeScore(score);
        redisTemplate.opsForZSet().add(LEADERBOARD_KEY, String.valueOf(userId), compositeScore);
        log.info("[리더보드 점수 갱신] 유저 {}: 원본점수={}점 (복합점수={})", userId, score, compositeScore);
    }

    // 상위 N위 랭커 목록 조회 (O(log N + M))
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

    // 내 실시간 순위 및 순수 점수 조회 (O(log N))
    public UserRankResponse getUserRank(Long userId) {
        Long rankIndex = redisTemplate.opsForZSet().reverseRank(LEADERBOARD_KEY, String.valueOf(userId));
        Double compositeScore = redisTemplate.opsForZSet().score(LEADERBOARD_KEY, String.valueOf(userId));

        if (rankIndex == null || compositeScore == null) {
            throw new IllegalArgumentException("리더보드에 등록되지 않은 유저입니다.");
        }

        return new UserRankResponse(userId, rankIndex.intValue() + 1, extractBaseScore(compositeScore));
    }
}