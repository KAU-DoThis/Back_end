package com.dothis.fintris.contextquestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "규칙 저장. 답한 질문의 가맹점·시간대(개인 송금은 받는 사람)와 답으로 만든다")
public record ContextRuleCreateRequest(
        @Schema(example = "56") @NotNull Long questionId
) {
}
