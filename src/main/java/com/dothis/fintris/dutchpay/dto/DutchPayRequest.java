package com.dothis.fintris.dutchpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

// 거래 목록의 [1/N] → 더치페이 화면에서 고른 결제·받은 돈
@Schema(description = "더치페이 생성 요청")
public record DutchPayRequest(
        @Schema(description = "결제(출금) 거래 ID. 1건 이상", example = "[120, 121]")
        @NotEmpty List<@NotNull Long> paymentTransactionIds,

        @Schema(description = "받은 돈(입금) 거래 ID. 아직 안 받았으면 빈 배열. 카카오페이에 있던 내 돈이 섞인 입금도 그대로 고른다",
                example = "[130, 131]")
        @NotNull List<@NotNull Long> receivedTransactionIds,

        @Schema(description = "내 몫 합(소비로 잡을 금액). 만들 때 비우면 서비스가 정한다(결제 합 - 받은 돈). "
                + "수정할 때 비우면 지금 값을 그대로 둔다. 결제 합보다 클 수 없다", example = "30000")
        @PositiveOrZero Long myShare
) {
}
