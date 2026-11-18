package com.dothis.fintris.dutchpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// 거래 목록의 [1/N] → 더치페이 화면에서 고른 결제·받은 돈
@Schema(description = "더치페이 생성 요청")
public record DutchPayRequest(
        @Schema(description = "결제(출금) 거래 ID. 1건 이상", example = "[120, 121]")
        @NotEmpty List<@NotNull Long> paymentTransactionIds,

        @Schema(description = "받은 돈(입금) 거래 ID. 아직 안 받았으면 빈 배열", example = "[130, 131]")
        @NotNull List<@NotNull Long> receivedTransactionIds
) {
}
