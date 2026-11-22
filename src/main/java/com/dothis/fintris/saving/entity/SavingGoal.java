package com.dothis.fintris.saving.entity;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.global.entity.BaseEntity;
import com.dothis.fintris.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 절약 목표 (기능명세서 F-03). 카테고리 하나의 지출을 기간 동안 목표 금액 아래로 줄인다
@Entity
@Table(name = "saving_goals")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavingGoal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "saving_goal_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private long baseAmount; // 기준 지출액 = 직전 3개월 평균 (서버 계산)

    @Column(nullable = false)
    private long targetAmount; // 목표 지출액

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SavingGoalStatus status = SavingGoalStatus.ACTIVE;

    // 확정 절약액을 먼저 채울 목표형 블록. "나중에"면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_block_id")
    private Block linkedBlock;

    // 어떤 절약 후보에서 만들었는지. 절약 후보 테이블이 생기면 연결한다
    private Long suggestionId;

    private Long actualAmount;    // 확정 때 실제 지출액
    private Long confirmedSaving; // 확정 절약액 = 기준 - 실제 (0 미만이면 0)
    private Long waitingFundId;   // 확정 때 만든 절약분 나눌 돈. 옮길 돈이 없었으면 null
    private LocalDateTime confirmedAt;

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public void cancel() {
        this.status = SavingGoalStatus.CANCELED;
    }

    public void confirm(long actualAmount, long confirmedSaving, Long waitingFundId, LocalDateTime now) {
        this.status = SavingGoalStatus.CONFIRMED;
        this.actualAmount = actualAmount;
        this.confirmedSaving = confirmedSaving;
        this.waitingFundId = waitingFundId;
        this.confirmedAt = now;
    }
}
