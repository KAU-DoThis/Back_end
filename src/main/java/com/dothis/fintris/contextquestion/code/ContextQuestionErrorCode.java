package com.dothis.fintris.contextquestion.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ContextQuestionErrorCode implements BaseErrorCode {
    CONTEXT_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTEXT_QUESTION_NOT_FOUND", "질문을 찾을 수 없습니다."),
    CONTEXT_QUESTION_FORBIDDEN(HttpStatus.FORBIDDEN, "CONTEXT_QUESTION_FORBIDDEN", "다른 사용자의 질문에는 답할 수 없습니다."),
    CONTEXT_QUESTION_CLOSED(HttpStatus.CONFLICT, "CONTEXT_QUESTION_CLOSED", "이미 답했거나 닫힌 질문입니다."),
    CONTEXT_QUESTION_INVALID_OPTION(HttpStatus.BAD_REQUEST, "CONTEXT_QUESTION_INVALID_OPTION", "선택지에 없는 답입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
