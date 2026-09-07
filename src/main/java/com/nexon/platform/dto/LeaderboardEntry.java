package com.nexon.platform.dto;

public record LeaderboardEntry(
        int rank,
        Long userId,
        Double score
) {}