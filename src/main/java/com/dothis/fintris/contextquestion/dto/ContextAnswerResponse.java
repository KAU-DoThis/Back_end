package com.dothis.fintris.contextquestion.dto;

import com.dothis.fintris.transaction.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "맥락 질문 답변 결과")
public record ContextAnswerResponse(
        @Schema(example = "56") Long questionId,
        @Schema(example = "LIVING") String tag,
        @Schema(example = "생활/생필품으로 바꾸고 생활비 블록에 그대로 둬요") String resultText,
        TransactionResponse transaction,
        @Schema(description = "같은 가맹점·시간대(개인 송금은 받는 사람)에 같은 답이 두 번째면 규칙 저장을 권한다. "
                + "수락하면 POST /context-rules에 questionId를 보낸다. 아니면 null") SuggestRule suggestRule
) {

    public record SuggestRule(
            @Schema(example = "지난번에도 이 시간대 네이버페이를 생활/생필품으로 답했어요. 다음부터 묻지 않고 자동으로 적용할까요?")
            String message
    ) {
    }
}
