package com.dothis.fintris.block.dto;

import com.dothis.fintris.block.entity.BlockColor;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 온보딩 3/3 초기 블록 구성안 (앱 시안 04, API 명세 v4 6장). 확정 전엔 아무것도 저장하지 않는다
@Schema(description = "초기 블록 구성안")
public record BlockProposalResponse(
        Summary summary,
        @Schema(description = "제안 블록. 사용자가 고친 뒤 /blocks/proposals/confirm으로 보낸다") List<Proposal> proposals,
        @Schema(description = "직접 추가할 수 있는 블록 (저축·비상금·여행)", example = "[\"SAVING\", \"EMERGENCY\", \"TRAVEL\"]")
        List<String> addablePresets,
        @Schema(description = "AI가 구성안을 만들었는지. false면 서버 기본안", example = "false") boolean aiAvailable
) {

    @Schema(description = "구성안 상단 요약")
    public record Summary(
            @Schema(description = "월 평균 수입. 거래 분석 전이면 null", example = "3500000") Long monthlyAvgIncome,
            @Schema(description = "찾은 반복 송금·결제 수", example = "0") int recurringCount,
            @Schema(description = "연동 계좌 잔액", example = "4920000") long linkedBalance,
            @Schema(description = "구성안 블록에 나눈 돈", example = "4920000") long allocated,
            @Schema(description = "나눌 돈으로 남는 돈", example = "0") long unallocated
    ) {
    }

    @Schema(description = "제안 블록")
    public record Proposal(
            @Schema(example = "spending-default") String proposalKey,
            @Schema(example = "SPENDING") BlockType type,
            @Schema(example = "생활비") String name,
            @Schema(example = "wallet") String icon,
            @Schema(example = "I") BlockColor color,
            @Schema(example = "true") boolean isDefault,
            @Schema(description = "처음 넣을 금액(원)", example = "1500000") long balance,
            BlockRule rule,
            @Schema(description = "AI가 제안한 금액. 거래 분석 전이면 null", example = "null") Long aiSuggestedAmount,
            @Schema(description = "최근 3달 평균 지출. 거래 분석 전이면 null (FE '평균보다 적어요' 경고용)", example = "null")
            Long recent3mAvg,
            @Schema(description = "하루 약 얼마 (월 금액 ÷ 이번 달 일수, 100원 내림)", example = "45100") long dailyApprox,
            @Schema(description = "고정지출을 어디서 찾았는지 (TRANSACTIONS). 기본안이면 null", example = "null") String foundFrom,
            @Schema(description = "제안 이유 한 줄") String reason
    ) {
    }
}
