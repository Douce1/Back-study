package com.nexon.platform.dto;

import java.time.LocalDateTime;

public record HallOfFameEntry(
        Integer rank,
        Long userId,
        Double score,
        LocalDateTime archivedAt
) {}