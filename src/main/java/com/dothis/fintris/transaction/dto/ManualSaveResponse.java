package com.dothis.fintris.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "빠른 입력 저장 결과")
public record ManualSaveResponse(
        TransactionResponse transaction,
        @Schema(description = "홈 dailyGuide.todayRemaining과 같은 값. 기본 블록 기간이 없으면 null", example = "40400")
        Long todayRemaining
) {
}
