package com.dothis.fintris.devtools;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩(블록 구성) 초기화 결과")
public record DevOnboardingResetResponse(
        @Schema(description = "지운 블록 수 (삭제한 블록 포함)", example = "5") int blocks,
        @Schema(description = "지운 이동·배분 기록 수", example = "3") int transfers,
        @Schema(description = "지운 나눌 돈 수", example = "1") int waitingFunds,
        @Schema(description = "블록 반영을 푼 거래 수 (거래 자체는 남는다)", example = "20") int releasedTransactions
) {
}
