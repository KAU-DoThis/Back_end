package com.dothis.fintris.saving.dto;

import com.dothis.fintris.saving.entity.SavingGoal;
import com.dothis.fintris.saving.entity.SavingGoalStatus;
import com.dothis.fintris.saving.service.SavingGoalCalculator;
import com.dothis.fintris.saving.service.SpentStats;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "절약 목표 객체")
public record SavingGoalResponse(
        @Schema(example = "9") Long savingGoalId,
        CategoryItem category,
        @Schema(description = "기준 지출액 = 지난달 지출", example = "84000") long baseAmount,
        @Schema(description = "기준 횟수 = 지난달 결제 건수", example = "6") int baseCount,
        @Schema(description = "1회 평균 금액 = 기준 지출액 ÷ 기준 횟수. 지난달에 쓴 적이 없으면 null", example = "14000")
        Long averageAmount,
        @Schema(description = "목표 지출액", example = "56000") long targetAmount,
        @Schema(description = "목표 횟수. 금액으로만 정했으면 null", example = "4") Integer targetCount,
        @Schema(description = "예상 절약액 = 기준 - 목표", example = "28000") long expectedSaving,
        @Schema(example = "2026-11-25") LocalDate startDate,
        @Schema(example = "2026-12-24") LocalDate endDate,
        @Schema(example = "ACTIVE") SavingGoalStatus status,
        @Schema(description = "기간 중 지금까지 쓴 돈. 확정됐으면 실제 지출액", example = "28000") long spent,
        @Schema(description = "기간 중 지금까지 결제 횟수. 확정됐으면 실제 횟수", example = "2") long spentCount,
        @Schema(description = "진행률(%) = 쓴 돈 ÷ 목표 지출액. 목표가 0원이면 null", example = "50") Integer progressRate,
        @Schema(description = "확정 절약액을 먼저 채울 블록. 없으면 null") LinkedBlock linkedBlock,
        @Schema(example = "21") Long suggestionId,
        @Schema(description = "확정 절약액 = 기준 - 실제 (0 미만이면 0). 확정 전 null", example = "null") Long confirmedSaving,
        @Schema(description = "확정 때 만든 절약분 나눌 돈. 확정 전이거나 옮길 돈이 없었으면 null", example = "null")
        Long waitingFundId
) {

    @Schema(description = "카테고리")
    public record CategoryItem(
            @Schema(example = "4") Long categoryId,
            @Schema(example = "TRANSPORT") String code,
            @Schema(example = "택시") String name
    ) {
    }

    @Schema(description = "연결 블록")
    public record LinkedBlock(@Schema(example = "4") Long blockId, @Schema(example = "여행") String name) {
    }

    // spent: 진행 중이면 지금까지 쓴 돈·횟수, 확정 뒤에는 저장한 실제 값. status는 다 나눴는지까지 반영한 값
    public static SavingGoalResponse of(SavingGoal goal, SpentStats spent, SavingGoalStatus status) {
        return new SavingGoalResponse(
                goal.getId(),
                new CategoryItem(goal.getCategory().getId(), goal.getCategory().getCode(), goal.getCategory().getName()),
                goal.getBaseAmount(),
                goal.getBaseCount(),
                SavingGoalCalculator.averageAmount(goal.getBaseAmount(), goal.getBaseCount()),
                goal.getTargetAmount(),
                goal.getTargetCount(),
                SavingGoalCalculator.expectedSaving(goal.getBaseAmount(), goal.getTargetAmount()),
                goal.getStartDate(),
                goal.getEndDate(),
                status,
                spent.amount(),
                spent.count(),
                SavingGoalCalculator.progressRate(spent.amount(), goal.getTargetAmount()),
                goal.getLinkedBlock() == null ? null
                        : new LinkedBlock(goal.getLinkedBlock().getId(), goal.getLinkedBlock().getName()),
                goal.getSuggestionId(),
                goal.getConfirmedSaving(),
                goal.getWaitingFundId());
    }
}
