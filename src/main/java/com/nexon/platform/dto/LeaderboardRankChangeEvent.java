package com.nexon.platform.dto;

public record LeaderboardRankChangeEvent(
        Long userId,
        int newRank,
        Double score,
        String eventType,
        long eventTimestamp
) {
    public static LeaderboardRankChangeEvent topRankEntry(Long userId, int newRank, Double score) {
        return new LeaderboardRankChangeEvent(
                userId,
                newRank,
                score,
                "TOP_RANK_ENTRY",
                System.currentTimeMillis()
        );
    }
}