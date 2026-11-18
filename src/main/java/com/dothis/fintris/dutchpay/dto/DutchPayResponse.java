package com.dothis.fintris.dutchpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "더치페이 결과")
public record DutchPayResponse(
        @Schema(example = "7") Long dutchPayId,
        List<Payment> payments,
        List<Received> received,
        @Schema(description = "결제 합", example = "40000") long paidTotal,
        @Schema(description = "받은 돈 합. 결제보다 많을 수 있다", example = "20000") long receivedTotal,
        @Schema(description = "내 몫 합. 소비로 잡히는 금액", example = "20000") long myShareTotal,
        @Schema(description = "블록에 돌려놓은 금액 합", example = "20000") long returnedTotal,
        @Schema(example = "0") Long version
) {

    public record Payment(
            @Schema(example = "120") Long transactionId,
            @Schema(example = "30000") long amount,
            @Schema(description = "받은 돈을 결제 금액 비율로 뺀 내 몫", example = "15000") long myShare,
            @Schema(description = "받은 돈을 돌려놓은 블록(이 결제가 반영된 블록). 블록에 반영되지 않은 결제면 null") ReturnedBlock returnedBlock
    ) {
    }

    public record Received(
            @Schema(example = "130") Long transactionId,
            @Schema(example = "15000") long amount
    ) {
    }

    public record ReturnedBlock(
            @Schema(example = "1") Long blockId,
            @Schema(example = "생활비") String name,
            @Schema(description = "돌려놓은 금액(결제 금액 - 내 몫)", example = "15000") long amount
    ) {
    }
}
