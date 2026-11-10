package com.dothis.fintris.block.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "초기 블록 구성 미리보기")
public record BlockProposalPreviewResponse(
        @Schema(description = "연동 계좌 잔액", example = "1850000") long linkedBalance,
        @Schema(description = "블록에 나눈 돈 (요청 금액 합)", example = "1850000") long allocated,
        @Schema(description = "나눌 돈으로 남는 돈. 넘치면 음수 (확정하면 BLOCK_INSUFFICIENT_FUNDS)", example = "0")
        long unallocated,
        List<Block> blocks
) {

    public record Block(
            @Schema(example = "spending-default") String proposalKey,
            @Schema(description = "하루 약 얼마 (월 금액 ÷ 이번 달 일수, 100원 내림). 사용형만", example = "40000")
            Long dailyApprox,
            @Schema(description = "경고 문장. 없으면 null", example = "최근 3달 평균보다 120,000원 적어요") String warning
    ) {
    }
}
