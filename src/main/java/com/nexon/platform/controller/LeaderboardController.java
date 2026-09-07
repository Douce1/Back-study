package com.nexon.platform.controller;

import com.nexon.platform.dto.CommonResponse;
import com.nexon.platform.dto.LeaderboardEntry;
import com.nexon.platform.dto.ScoreSubmitRequest;
import com.nexon.platform.dto.UserRankResponse;
import com.nexon.platform.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "03. Leaderboard API", description = "Redis Sorted Set(ZSET) 기반 실시간 랭킹 시스템")
@RestController
@RequestMapping("/api/v1/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @Operation(summary = "내 게임 점수 등록/갱신 (ZADD)")
    @PostMapping("/score")
    public CommonResponse<Void> submitScore(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ScoreSubmitRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요한 서비스입니다.");
        }
        leaderboardService.submitScore(userId, request.score());
        return CommonResponse.ok("점수가 성공적으로 반영되었습니다.", null);
    }

    @Operation(summary = "실시간 상위 랭커 조회 (Top N)")
    @GetMapping("/top")
    public CommonResponse<List<LeaderboardEntry>> getTopRankers(
            @RequestParam(defaultValue = "10") int limit) {
        List<LeaderboardEntry> topRankers = leaderboardService.getTopRankers(limit);
        return CommonResponse.ok("실시간 Top " + limit + " 랭킹 조회 완료", topRankers);
    }

    @Operation(summary = "내 실시간 순위 및 점수 조회")
    @GetMapping("/my-rank")
    public CommonResponse<UserRankResponse> getMyRank(
            @AuthenticationPrincipal Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요한 서비스입니다.");
        }
        UserRankResponse rankResponse = leaderboardService.getUserRank(userId);
        return CommonResponse.ok("내 랭킹 조회 완료", rankResponse);
    }
}