package com.dothis.fintris.contextquestion.dto;

import com.dothis.fintris.contextquestion.entity.ContextRule;
import com.dothis.fintris.contextquestion.entity.QuestionType;
import com.dothis.fintris.contextquestion.entity.RuleMatch;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "맥락 규칙")
public record ContextRuleResponse(
        @Schema(example = "3") Long ruleId,
        @Schema(example = "UNKNOWN_ITEM") QuestionType questionType,
        @Schema(description = "어떤 거래에 적용되는지", example = "네이버페이 · 저녁 15~22시") String target,
        @Schema(description = "적용할 답. 카테고리 이름, 야간 배달은 고른 답", example = "생활/생필품") String answer,
        LocalDateTime createdAt
) {

    public static ContextRuleResponse from(ContextRule rule) {
        RuleMatch match = rule.getMatch();
        String target = match.payee() != null
                ? match.payee() + "에게 보낸 돈"
                : match.merchant() + " · " + match.timeSlot().getLabel();
        String answer = rule.getCategory() != null ? rule.getCategory().getName() : rule.getTag();
        return new ContextRuleResponse(rule.getId(), rule.getQuestionType(), target, answer, rule.getCreatedAt());
    }
}
