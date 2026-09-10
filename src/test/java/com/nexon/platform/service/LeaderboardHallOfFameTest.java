package com.nexon.platform.service;

import com.nexon.platform.dto.HallOfFameEntry;
import com.nexon.platform.dto.PageResponse;
import com.nexon.platform.entity.SeasonLeaderboardSnapshot;
import com.nexon.platform.repository.SeasonLeaderboardSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LeaderboardHallOfFameTest {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private SeasonLeaderboardSnapshotRepository snapshotRepository;

    @BeforeEach
    void setUp() {
        snapshotRepository.deleteAll();
    }

    @Test
    @DisplayName("과거 시즌 데이터 25건이 존재할 때 페이징(Page 0, 2) 요청 시 순위 오름차순과 메타데이터가 정확히 반환되어야 한다")
    void getHallOfFame_Paging_Success() {
        // Given: 시즌 1에 25명의 최종 스냅샷 데이터 저장
        int seasonId = 1;
        int totalCount = 25;
        List<SeasonLeaderboardSnapshot> dummies = new ArrayList<>();
        for (int rank = 1; rank <= totalCount; rank++) {
            long userId = 1000L + rank;
            double score = 10000.0 - (rank * 10.0);
            dummies.add(new SeasonLeaderboardSnapshot(seasonId, userId, rank, score));
        }
        snapshotRepository.saveAll(dummies);

        // When 1: 첫 번째 페이지 조회 (page=0, size=10) -> 1등~10등
        PageResponse<HallOfFameEntry> firstPage = leaderboardService.getHallOfFame(seasonId, 0, 10);

        // Then 1: 첫 페이지 검증
        assertThat(firstPage.currentPage()).isEqualTo(0);
        assertThat(firstPage.pageSize()).isEqualTo(10);
        assertThat(firstPage.totalElements()).isEqualTo(25L);
        assertThat(firstPage.totalPages()).isEqualTo(3);
        assertThat(firstPage.isLast()).isFalse();
        assertThat(firstPage.content()).hasSize(10);
        assertThat(firstPage.content().get(0).rank()).isEqualTo(1);
        assertThat(firstPage.content().get(9).rank()).isEqualTo(10);

        // When 2: 마지막 페이지 조회 (page=2, size=10) -> 21등~25등
        PageResponse<HallOfFameEntry> lastPage = leaderboardService.getHallOfFame(seasonId, 2, 10);

        // Then 2: 마지막 페이지 검증
        assertThat(lastPage.currentPage()).isEqualTo(2);
        assertThat(lastPage.isLast()).isTrue();
        assertThat(lastPage.content()).hasSize(5);
        assertThat(lastPage.content().get(0).rank()).isEqualTo(21);
        assertThat(lastPage.content().get(4).rank()).isEqualTo(25);
    }
}