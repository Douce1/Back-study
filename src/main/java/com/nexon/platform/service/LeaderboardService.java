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

    private final StringRedisTemplate redisTemplate;

    public LeaderboardService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 점수 등록 및 실시간 랭킹 반영 (O(log N))
    public void submitScore(Long userId, Double score) {
        redisTemplate.opsForZSet().add(LEADERBOARD_KEY, String.valueOf(userId), score);
        log.info("[리더보드 점수 갱신] 유저 {}: {}점 반영 완료", userId, score);
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
            Double score = tuple.getScore();
            result.add(new LeaderboardEntry(currentRank++, userId, score));
        }

        return result;
    }

    // 내 실시간 순위 및 점수 조회 (O(log N))
    public UserRankResponse getUserRank(Long userId) {
        Long rankIndex = redisTemplate.opsForZSet().reverseRank(LEADERBOARD_KEY, String.valueOf(userId));
        Double score = redisTemplate.opsForZSet().score(LEADERBOARD_KEY, String.valueOf(userId));

        if (rankIndex == null || score == null) {
            throw new IllegalArgumentException("리더보드에 등록되지 않은 유저입니다.");
        }

        // Redis Rank는 0부터 시작하므로 +1 처리
        return new UserRankResponse(userId, rankIndex.intValue() + 1, score);
    }
}