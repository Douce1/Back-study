package com.nexon.platform.dto;

import jakarta.validation.constraints.NotNull;

public record ScoreSubmitRequest(
        @NotNull(message = "점수는 필수 입력 항목입니다.")
        Double score
) {}