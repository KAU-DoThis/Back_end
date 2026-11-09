package com.dothis.fintris.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "제안 수락")
public record ProposalAcceptRequest(
        @Schema(description = "requiresConfirm 카드에서 한 번 더 확인했는지", example = "false") Boolean confirmed
) {
}
