package com.dothis.fintris.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// "여행에서 생활비로 5만원 옮겨줘"에서 뽑은 값
@Schema(description = "블록 이동 제안 만들기")
public record ProposalCreateRequest(
        @Schema(description = "보내는 블록", example = "3") @NotNull Long fromBlockId,
        @Schema(description = "받는 블록", example = "1") @NotNull Long toBlockId,
        @Schema(description = "금액(원)", example = "50000") @NotNull @Positive Long amount
) {
}
