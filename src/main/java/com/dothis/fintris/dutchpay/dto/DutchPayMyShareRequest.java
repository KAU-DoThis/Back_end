package com.dothis.fintris.dutchpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "내 몫 수정")
public record DutchPayMyShareRequest(
        @Schema(description = "내 몫 합. null이면 서비스가 정한 값(결제 합 - 받은 돈)으로 되돌린다", example = "30000")
        @PositiveOrZero Long myShare
) {
}
