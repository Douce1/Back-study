package com.nexon.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "season_leaderboard_snapshot", indexes = {
        @Index(name = "idx_season_final_rank", columnList = "season_id, final_rank")
})
public class SeasonLeaderboardSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "season_id", nullable = false)
    private Integer seasonId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "final_rank", nullable = false)
    private Integer finalRank;

    @Column(name = "final_score", nullable = false)
    private Double finalScore;

    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;

    protected SeasonLeaderboardSnapshot() {}

    public SeasonLeaderboardSnapshot(Integer seasonId, Long userId, Integer finalRank, Double finalScore) {
        this.seasonId = seasonId;
        this.userId = userId;
        this.finalRank = finalRank;
        this.finalScore = finalScore;
        this.archivedAt = LocalDateTime.now();
    }

    public Long getSnapshotId() { return snapshotId; }
    public Integer getSeasonId() { return seasonId; }
    public Long getUserId() { return userId; }
    public Integer getFinalRank() { return finalRank; }
    public Double getFinalScore() { return finalScore; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
}