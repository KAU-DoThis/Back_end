package com.dothis.fintris.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "초불이에게 말 걸기")
public record CoachMessageRequest(
        @Schema(description = "방에 들어올 때 받은 대화 id", example = "6f1c2e0a-3b7d-4c55-9a1e-2d8f0b9c7a41") @NotBlank String sessionId,
        @Schema(example = "여행에서 생활비로 5만원 옮겨줘") @NotBlank @Size(max = 500) String text
) {
}
