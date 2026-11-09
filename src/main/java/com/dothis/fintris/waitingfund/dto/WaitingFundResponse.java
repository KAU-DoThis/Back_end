package com.dothis.fintris.waitingfund.dto;

import com.dothis.fintris.waitingfund.entity.WaitingFund;
import com.dothis.fintris.waitingfund.entity.WaitingFundSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// API 명세 v4 0장 대기 자금 객체 (홈 "나눌 돈" 카드)
@Schema(description = "나눌 돈(대기 자금)")
public record WaitingFundResponse(
        @Schema(example = "12") Long waitingFundId,
        @Schema(description = "SAVING · REGULAR_INCOME · EXTRA_INCOME · OTHER", example = "SAVING") WaitingFundSource source,
        @Schema(example = "절약분") String label,
        @Schema(example = "9월 절약 확정") String description,
        @Schema(description = "남은 금액(원). 0이 되면 목록에서 빠진다", example = "87000") long amount,
        @Schema(description = "처음 금액(원)", example = "87000") long originalAmount,
        @Schema(example = "2026-10-01T06:00:00") LocalDateTime createdAt
) {
    public static WaitingFundResponse from(WaitingFund fund) {
        return new WaitingFundResponse(
                fund.getId(),
                fund.getSource(),
                fund.getLabel(),
                fund.getDescription(),
                fund.getAmount(),
                fund.getOriginalAmount(),
                fund.getCreatedAt()
        );
    }
}
