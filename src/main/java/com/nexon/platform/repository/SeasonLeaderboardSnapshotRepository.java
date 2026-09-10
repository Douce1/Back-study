package com.nexon.platform.repository;

import com.nexon.platform.entity.SeasonLeaderboardSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeasonLeaderboardSnapshotRepository extends JpaRepository<SeasonLeaderboardSnapshot, Long> {

    // 특정 시즌의 최종 순위 리스트 전체 조회 (기존 유지)
    List<SeasonLeaderboardSnapshot> findBySeasonIdOrderByFinalRankAsc(Integer seasonId);

    // 해당 시즌에 이미 아카이빙된 레코드 수가 존재하는지 검증 (기존 유지)
    long countBySeasonId(Integer seasonId);

    // [신규] 특정 시즌의 랭킹 데이터를 복합 인덱스를 통해 순위 오름차순으로 페이징 조회
    Page<SeasonLeaderboardSnapshot> findBySeasonIdOrderByFinalRankAsc(Integer seasonId, Pageable pageable);
}