package com.dothis.fintris.contextquestion.dto;

import com.dothis.fintris.contextquestion.entity.QuestionStatus;
import com.dothis.fintris.contextquestion.entity.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// API 명세 v4 9장 맥락 질문 객체
@Schema(description = "맥락 질문")
public record ContextQuestionResponse(
        @Schema(example = "56") Long questionId,
        @Schema(example = "120") Long transactionId,
        @Schema(example = "UNKNOWN_ITEM") QuestionType type,
        @Schema(example = "PENDING") QuestionStatus status,
        @Schema(example = "어제 결제한 네이버페이 38,000원, 무엇을 샀어요?") String text,
        @Schema(example = "10월 13일 화 19:22 · 신한카드 ••12") String meta,
        @Schema(description = "맨 앞이 가장 그럴듯한 답") List<Option> options,
        // TODO: 직접 적은 답을 AI 서버가 태그로 바꾸는 API가 붙으면 true로 바꾼다
        @Schema(description = "직접 적기를 받을 수 있는지", example = "false") boolean allowFreeText,
        @Schema(example = "적은 내용은 분류에만 쓰고 저장하지 않아요") String freeTextNotice
) {

    public record Option(
            @Schema(description = "답변할 때 보내는 값", example = "LIVING") String tag,
            @Schema(example = "생활/생필품") String label
    ) {
    }
}
