package com.dothis.fintris.transaction.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TransactionErrorCode implements BaseErrorCode {
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "거래를 찾을 수 없습니다."),
    TRANSACTION_FORBIDDEN(HttpStatus.FORBIDDEN, "TRANSACTION_FORBIDDEN", "다른 사용자의 거래에는 접근할 수 없습니다."),
    TRANSACTION_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSACTION_CATEGORY_NOT_FOUND", "존재하지 않는 카테고리입니다."),
    MANUAL_PARSE_FAILED(HttpStatus.BAD_REQUEST, "MANUAL_PARSE_FAILED", "금액을 읽지 못했어요. 예: 붕어빵 3000"),
    TRANSACTION_NOT_REVIEWABLE(HttpStatus.CONFLICT, "TRANSACTION_NOT_REVIEWABLE", "확인이 필요한 거래가 아닙니다."),
    TRANSACTION_NOT_DELETABLE(HttpStatus.CONFLICT, "TRANSACTION_NOT_DELETABLE",
            "은행·카드에서 가져온 거래는 지울 수 없습니다. 유지하거나 카테고리를 고쳐 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
