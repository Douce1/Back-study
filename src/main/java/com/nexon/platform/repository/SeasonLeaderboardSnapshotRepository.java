package com.nexon.platform.repository;

import com.nexon.platform.entity.SeasonLeaderboardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeasonLeaderboardSnapshotRepository extends JpaRepository<SeasonLeaderboardSnapshot, Long> {

    // 특정 시즌의 최종 순위 리스트를 1등부터 순서대로 조회
    List<SeasonLeaderboardSnapshot> findBySeasonIdOrderByFinalRankAsc(Integer seasonId);

    // 해당 시즌에 이미 아카이빙된 레코드 수가 존재하는지 검증
    long countBySeasonId(Integer seasonId);
}