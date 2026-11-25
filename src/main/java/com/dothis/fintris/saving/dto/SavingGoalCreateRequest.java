package com.dothis.fintris.saving.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "절약 목표 생성 요청 (12 절약 목표 설정). 목표는 횟수(targetCount)나 금액(targetAmount) 중 하나로 정한다")
public record SavingGoalCreateRequest(
        @Schema(example = "4") @NotNull Long categoryId,
        @Schema(description = "목표 횟수. 보내면 목표 금액은 서버가 지난달 1회 평균으로 계산한다. 지난달 횟수보다 적어야 한다",
                example = "4") Integer targetCount,
        @Schema(description = "목표 지출액(원). targetCount를 안 보낼 때 필수. 지난달 지출보다 작아야 한다",
                example = "null") Long targetAmount,
        @Schema(description = "비우면 오늘", example = "2026-11-25") LocalDate startDate,
        @Schema(description = "비우면 시작일부터 1개월(시작일 전날까지)", example = "2026-12-24") LocalDate endDate,
        @Schema(description = "확정 절약액을 먼저 채울 목표형 블록. \"나중에\"면 null", example = "4") Long linkedBlockId,
        @Schema(description = "절약 후보에서 만들었으면 그 후보 ID", example = "21") Long suggestionId
) {
}
