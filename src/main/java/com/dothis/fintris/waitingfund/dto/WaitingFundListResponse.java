package com.dothis.fintris.waitingfund.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "나눌 돈 목록")
public record WaitingFundListResponse(
        @Schema(description = "남은 금액이 있는 것만, 생긴 순서") List<WaitingFundResponse> waitingFunds,
        @Schema(description = "남은 금액 합계(원)", example = "207000") long total
) {
    public static WaitingFundListResponse of(List<WaitingFundResponse> waitingFunds) {
        long total = waitingFunds.stream().mapToLong(WaitingFundResponse::amount).sum();
        return new WaitingFundListResponse(waitingFunds, total);
    }
}
