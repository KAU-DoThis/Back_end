package com.dothis.fintris.coach.dto;

import com.dothis.fintris.coach.entity.ProposalStatus;
import io.swagger.v3.oas.annotations.media.Schema;

// 제안을 만들거나 수락·거절·되돌린 뒤 초불이가 할 말과 카드. 문장은 서버가 틀에 숫자를 채워 만든다
@Schema(description = "초불이 답과 카드")
public record ProposalActionResponse(
        @Schema(example = "31") Long proposalId,
        @Schema(example = "ACCEPTED") ProposalStatus status,
        @Schema(description = "초불이 말풍선 문장", example = "50,000원 옮겼어요") String text,
        @Schema(description = "TransferProposalCard 또는 TransferResultCard. 거절이면 null") Object card
) {
}
