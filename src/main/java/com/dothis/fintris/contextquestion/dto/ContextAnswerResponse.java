package com.dothis.fintris.contextquestion.dto;

import com.dothis.fintris.transaction.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "맥락 질문 답변 결과")
public record ContextAnswerResponse(
        @Schema(example = "56") Long questionId,
        @Schema(example = "LIVING") String tag,
        @Schema(example = "생활/생필품으로 바꾸고 생활비 블록에 그대로 둬요") String resultText,
        TransactionResponse transaction
) {
}
