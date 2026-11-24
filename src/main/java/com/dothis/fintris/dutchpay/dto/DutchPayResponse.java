package com.dothis.fintris.dutchpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "더치페이 결과")
public record DutchPayResponse(
        @Schema(example = "7") Long dutchPayId,
        List<Payment> payments,
        List<Received> received,
        @Schema(description = "결제 합", example = "40000") long paidTotal,
        @Schema(description = "받은 돈 합. 결제보다 많을 수 있다(카카오페이에 있던 내 돈이 섞인 입금 등)", example = "140000") long receivedTotal,
        @Schema(description = "내 몫 합. 소비 분석·블록 쓴 돈에 잡히는 금액", example = "30000") long myShareTotal,
        @Schema(description = "사용자가 내 몫을 직접 정했으면 true, 결제 합 - 받은 돈으로 계산했으면 false", example = "true") boolean myShareEdited,
        @Schema(description = "블록에 돌려놓은 금액 합 (결제 합 - 내 몫, 받은 돈까지만)", example = "90000") long returnedTotal,
        @Schema(description = "아직 받을 돈 (결제 합 - 내 몫 - 받은 돈). 받으면 그 입금을 붙인다", example = "0") long outstandingAmount,
        @Schema(description = "받은 돈 중 나눌 돈으로 넣은 금액 (받은 돈 - 블록에 돌려놓은 돈)", example = "50000") long bufferAmount,
        @Schema(description = "그 나눌 돈. 없으면 null", example = "12") Long waitingFundId,
        @Schema(example = "0") Long version
) {

    public record Payment(
            @Schema(example = "120") Long transactionId,
            @Schema(example = "30000") long amount,
            @Schema(description = "내 몫 합을 결제 금액 비율로 나눈 이 결제의 내 몫", example = "15000") long myShare,
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
            @Schema(description = "돌려놓은 금액(결제 금액 - 내 몫, 받은 돈까지만)", example = "15000") long amount
    ) {
    }
}
