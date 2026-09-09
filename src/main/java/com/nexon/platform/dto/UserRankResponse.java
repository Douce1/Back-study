package com.nexon.platform.dto;

public record UserRankResponse(
        Long userId,
        int rank,
        Double score,
        Double topPercentile, // 상위 백분율 (예: 1.25 -> 상위 1.25%)
        Long totalPlayers     // 전체 참가자 수
) {}