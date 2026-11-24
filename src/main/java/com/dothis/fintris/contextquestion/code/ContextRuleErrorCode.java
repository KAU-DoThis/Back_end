package com.dothis.fintris.contextquestion.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ContextRuleErrorCode implements BaseErrorCode {
    CONTEXT_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTEXT_RULE_NOT_FOUND", "규칙을 찾을 수 없습니다."),
    CONTEXT_RULE_FORBIDDEN(HttpStatus.FORBIDDEN, "CONTEXT_RULE_FORBIDDEN", "다른 사용자의 규칙에는 접근할 수 없습니다."),
    CONTEXT_RULE_NOT_ANSWERED(HttpStatus.CONFLICT, "CONTEXT_RULE_NOT_ANSWERED", "답한 질문으로만 규칙을 만들 수 있습니다."),
    CONTEXT_RULE_NO_MATCH(HttpStatus.BAD_REQUEST, "CONTEXT_RULE_NO_MATCH", "가맹점이나 받는 사람 이름이 없는 거래는 규칙으로 만들 수 없습니다."),
    CONTEXT_RULE_DUPLICATE(HttpStatus.CONFLICT, "CONTEXT_RULE_DUPLICATE", "이미 같은 규칙이 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
