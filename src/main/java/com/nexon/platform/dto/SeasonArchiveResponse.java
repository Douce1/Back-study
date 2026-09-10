package com.nexon.platform.dto;

public record SeasonArchiveResponse(
        Integer seasonId,
        long totalArchived,
        Long topUserId,
        Double topScore
) {}