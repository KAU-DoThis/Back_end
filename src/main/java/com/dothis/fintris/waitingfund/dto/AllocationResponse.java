package com.dothis.fintris.waitingfund.dto;

import com.dothis.fintris.block.dto.TransferResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "나눌 돈 배분 결과")
public record AllocationResponse(
        FundBalance waitingFund,
        @Schema(description = "배분받은 블록") List<TransferResponse.BlockBalance> blocks,
        @Schema(description = "기본 사용형 블록 기준 오늘 쓸 수 있는 돈") TransferResponse.DailyGuide dailyGuide
) {

    public record FundBalance(
            @Schema(example = "12") Long waitingFundId,
            @Schema(description = "남은 금액. 0이면 홈 카드가 사라진다", example = "0") long amount
    ) {
    }
}
