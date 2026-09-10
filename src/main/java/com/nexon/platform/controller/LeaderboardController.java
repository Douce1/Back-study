package com.nexon.platform.controller;

import com.nexon.platform.dto.CommonResponse;
import com.nexon.platform.dto.LeaderboardEntry;
import com.nexon.platform.dto.ScoreSubmitRequest;
import com.nexon.platform.dto.SeasonArchiveResponse;
import com.nexon.platform.dto.UserRankResponse;
import com.nexon.platform.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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

    @Operation(summary = "내 실시간 순위, 점수 및 상위 백분위(Percentile) 조회")
    @GetMapping("/my-rank")
    public CommonResponse<UserRankResponse> getMyRank(
            @AuthenticationPrincipal Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요한 서비스입니다.");
        }
        UserRankResponse rankResponse = leaderboardService.getUserRank(userId);
        return CommonResponse.ok("내 랭킹 및 상위 백분위 조회 완료", rankResponse);
    }

    @Operation(summary = "[테스트 검증용] 1,000명 대규모 더미 플레이어 점수 일괄 생성 (파이프라이닝)")
    @PostMapping("/dummy-populate")
    public CommonResponse<String> populateDummyScores(
            @RequestParam(defaultValue = "1000") int count) {
        List<LeaderboardService.ScoreData> dummyList = new ArrayList<>(count);
        for (long i = 100; i < 100 + count; i++) {
            double score = 1000.0 + ((i - 100) * 9.0);
            dummyList.add(new LeaderboardService.ScoreData(i, score));
        }
        leaderboardService.bulkRegisterScores(dummyList);
        return CommonResponse.ok(count + "명의 더미 경쟁자 데이터가 Redis 파이프라이닝으로 초고속 적재되었습니다.", null);
    }

    @Operation(summary = "시즌 종료 정산 및 RDBMS 스냅샷 영속화 (Redis -> MySQL 아카이빙 & 시즌 리셋)")
    @PostMapping("/season/{seasonId}/archive")
    public CommonResponse<SeasonArchiveResponse> archiveSeason(@PathVariable int seasonId) {
        SeasonArchiveResponse response = leaderboardService.archiveSeason(seasonId);
        return CommonResponse.ok("시즌 " + seasonId + " 랭킹이 RDBMS로 성공적으로 아카이빙되고 시즌이 초기화되었습니다.", response);
    }
}