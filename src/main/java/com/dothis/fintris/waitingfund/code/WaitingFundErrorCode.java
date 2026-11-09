package com.dothis.fintris.waitingfund.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum WaitingFundErrorCode implements BaseErrorCode {
    WAITING_FUND_NOT_FOUND(HttpStatus.NOT_FOUND, "WAITING_FUND_NOT_FOUND", "나눌 돈을 찾을 수 없습니다."),
    WAITING_FUND_FORBIDDEN(HttpStatus.FORBIDDEN, "WAITING_FUND_FORBIDDEN", "다른 사용자의 나눌 돈에는 접근할 수 없습니다."),
    WAITING_FUND_INSUFFICIENT(HttpStatus.BAD_REQUEST, "WAITING_FUND_INSUFFICIENT", "나눌 돈보다 큰 금액입니다."),
    WAITING_FUND_VERSION_CONFLICT(HttpStatus.CONFLICT, "WAITING_FUND_VERSION_CONFLICT", "다른 곳에서 나눌 돈이 바뀌었습니다. 새로 불러온 뒤 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
