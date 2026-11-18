package com.dothis.fintris.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "확인 필요 거래 처리 결과")
public record TransactionReviewResponse(
        @Schema(example = "101") Long transactionId,
        @Schema(example = "KEEP") TransactionReviewRequest.Action action,
        @Schema(description = "유지했으면 확정된 거래, 삭제했으면 null") TransactionResponse transaction
) {
}
