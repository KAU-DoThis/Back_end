package com.dothis.fintris.contextquestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "맥락 질문 답변")
public record ContextAnswerRequest(
        @Schema(description = "고른 선택지의 tag", example = "LIVING") @NotBlank String tag
) {
}
