package com.dothis.fintris.dutchpay.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum DutchPayErrorCode implements BaseErrorCode {
    DUTCH_PAY_NOT_FOUND(HttpStatus.NOT_FOUND, "DUTCH_PAY_NOT_FOUND", "더치페이를 찾을 수 없습니다."),
    DUTCH_PAY_FORBIDDEN(HttpStatus.FORBIDDEN, "DUTCH_PAY_FORBIDDEN", "다른 사용자의 더치페이에는 접근할 수 없습니다."),
    DUTCH_PAY_DUPLICATE_TRANSACTION(HttpStatus.BAD_REQUEST, "DUTCH_PAY_DUPLICATE_TRANSACTION", "같은 거래를 두 번 고를 수 없습니다."),
    DUTCH_PAY_INVALID_PAYMENT(HttpStatus.BAD_REQUEST, "DUTCH_PAY_INVALID_PAYMENT", "결제는 나간 돈(출금)만 고를 수 있습니다."),
    DUTCH_PAY_INVALID_RECEIVED(HttpStatus.BAD_REQUEST, "DUTCH_PAY_INVALID_RECEIVED", "받은 돈은 들어온 돈(입금)만 고를 수 있습니다."),
    DUTCH_PAY_EXCLUDED_PAYMENT(HttpStatus.BAD_REQUEST, "DUTCH_PAY_EXCLUDED_PAYMENT", "소비에서 뺀 출금은 결제로 고를 수 없습니다."),
    DUTCH_PAY_CANCELED_TRANSACTION(HttpStatus.BAD_REQUEST, "DUTCH_PAY_CANCELED_TRANSACTION", "취소된 거래는 고를 수 없습니다."),
    DUTCH_PAY_ALREADY_LINKED(HttpStatus.CONFLICT, "DUTCH_PAY_ALREADY_LINKED", "이미 다른 더치페이에 들어간 거래입니다."),
    DUTCH_PAY_INVALID_MY_SHARE(HttpStatus.BAD_REQUEST, "DUTCH_PAY_INVALID_MY_SHARE", "내 몫은 결제 합보다 클 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
