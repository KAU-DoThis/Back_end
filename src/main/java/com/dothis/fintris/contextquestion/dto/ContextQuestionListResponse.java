package com.dothis.fintris.contextquestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "맥락 질문 목록")
public record ContextQuestionListResponse(
        @Schema(description = "최근 거래 순") List<ContextQuestionResponse> questions
) {
}
