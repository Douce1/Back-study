package com.nexon.platform.dto;

public record UserRankResponse(
        Long userId,
        int rank,
        Double score
) {}