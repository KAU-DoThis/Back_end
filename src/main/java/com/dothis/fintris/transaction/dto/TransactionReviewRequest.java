package com.dothis.fintris.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "확인 필요 거래 처리")
public record TransactionReviewRequest(
        @Schema(description = "KEEP: 맞는 거래로 유지, DELETE: 삭제하고 블록 잔액 복구", example = "KEEP")
        @NotNull Action action
) {

    public enum Action {
        KEEP, DELETE
    }
}
