package com.dothis.fintris.saving.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "절약 목표 생성 요청 (12 절약 목표 설정)")
public record SavingGoalCreateRequest(
        @Schema(example = "4") @NotNull Long categoryId,
        @Schema(description = "목표 지출액(원). 기준 지출액보다 작아야 한다", example = "42000") @NotNull Long targetAmount,
        @Schema(description = "비우면 오늘", example = "2026-11-22") LocalDate startDate,
        @Schema(description = "비우면 시작일부터 1개월(시작일 전날까지)", example = "2026-12-21") LocalDate endDate,
        @Schema(description = "확정 절약액을 먼저 채울 목표형 블록. \"나중에\"면 null", example = "4") Long linkedBlockId,
        @Schema(description = "절약 후보에서 만들었으면 그 후보 ID", example = "21") Long suggestionId
) {
}
