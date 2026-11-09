package com.dothis.fintris.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초불이 답")
public record CoachMessageResponse(
        @Schema(description = "초불이 말풍선 문장", example = "이렇게 옮길까요?") String text,
        @Schema(description = "이동을 시켰으면 TransferProposalCard, 아니면 null") Object card,
        @Schema(description = "AI 서버가 답하지 못해 고정 문장으로 답했으면 false", example = "true") boolean aiAvailable
) {
}
