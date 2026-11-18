package com.dothis.fintris.devtools;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "시연 거래 넣기 결과")
public record DevDemoTransactionsResponse(
        @Schema(description = "거래를 붙인 테스트 계좌", example = "12") Long linkedAccountId,
        @Schema(description = "새로 넣은 거래 수", example = "197") int inserted,
        @Schema(description = "이미 있어서 건너뛴 거래 수", example = "0") int skipped
) {
}
