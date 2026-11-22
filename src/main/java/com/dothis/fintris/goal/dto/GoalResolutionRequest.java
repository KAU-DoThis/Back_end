package com.dothis.fintris.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "목표 달성·기한 경과 처리 요청")
public record GoalResolutionRequest(
        @Schema(description = "KEEP·RAISE_TARGET·MOVE(달성), EXTEND·FINISH_AT_CURRENT·CLEANUP(기한 지남), REMIND_LATER(둘 다)",
                example = "EXTEND")
        @NotNull GoalAction action,

        @Schema(description = "EXTEND에 필수. RAISE_TARGET이면 함께 바꿀 수 있다", example = "2027-01-31")
        LocalDate newDueDate,

        @Schema(description = "RAISE_TARGET에 필수. 지금 목표 금액보다 커야 한다", example = "null")
        @Positive Long newTargetAmount,

        @Schema(description = "MOVE에 필수, CLEANUP은 선택. CLEANUP에서 옮기고 남은 돈은 나눌 돈(남은 돈)으로 돌아간다")
        List<@Valid Move> moveTo
) {

    @Schema(description = "옮길 블록과 금액")
    public record Move(
            @Schema(example = "1") @NotNull Long blockId,
            @Schema(example = "100000") @NotNull @Positive Long amount
    ) {
    }
}
