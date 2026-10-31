package com.dothis.fintris.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "계좌별 거래내역 동기화 결과")
public record AccountSyncResponse(
        @Schema(example = "1") Long linkedAccountId,
        @Schema(description = "이번에 새로 저장한 거래 수", example = "42") int newTransactions) {
}
