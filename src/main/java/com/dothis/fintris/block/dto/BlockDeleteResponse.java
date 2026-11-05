package com.dothis.fintris.block.dto;

import com.dothis.fintris.waitingfund.dto.WaitingFundResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "블록 삭제 결과")
public record BlockDeleteResponse(
        @Schema(example = "3") Long blockId,
        @Schema(description = "나눌 돈으로 돌아간 금액(원)", example = "240000") long returnedAmount,
        @Schema(description = "잔액이 담긴 나눌 돈(source: OTHER). 잔액이 0이었으면 null") WaitingFundResponse waitingFund
) {
}
