package com.dothis.fintris.dutchpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "더치페이 결과")
public record DutchPayResponse(
        @Schema(example = "7") Long dutchPayId,
        List<Payment> payments,
        List<Received> received,
        @Schema(description = "결제 합", example = "40000") long paidTotal,
        @Schema(description = "받은 돈 합. 결제보다 많을 수 있다", example = "30000") long receivedTotal,
        @Schema(description = "내 몫 합. 소비로 잡히는 금액", example = "10000") long myShareTotal,
        @Schema(description = "받은 돈을 돌려놓은 블록. 가장 큰 결제가 블록에 반영되지 않았으면 null") ReturnedBlock returnedBlock,
        @Schema(example = "0") Long version
) {

    public record Payment(
            @Schema(example = "120") Long transactionId,
            @Schema(example = "30000") long amount,
            @Schema(description = "받은 돈을 결제 금액 비율로 뺀 내 몫", example = "7500") long myShare
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
            @Schema(description = "블록에 더한 금액. 결제 합까지만", example = "30000") long amount
    ) {
    }
}
