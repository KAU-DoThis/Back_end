package com.dothis.fintris.block.dto;

import com.dothis.fintris.user.entity.OnboardingStep;
import com.dothis.fintris.waitingfund.dto.WaitingFundResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "초기 블록 확정 결과")
public record BlockProposalConfirmResponse(
        @Schema(description = "만든 블록 (displayOrder 순)") List<BlockResponse> blocks,
        @Schema(description = "블록에 나누고 남은 돈 ('남은 돈' 나눌 돈). 남은 게 없으면 빈 배열") List<WaitingFundResponse> waitingFunds,
        @Schema(description = "확정 후 온보딩 단계. 필수 동의·연동이 끝났으면 DONE", example = "DONE") OnboardingStep onboardingStep
) {
}
