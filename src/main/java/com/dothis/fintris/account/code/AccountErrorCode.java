package com.dothis.fintris.account.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AccountErrorCode implements BaseErrorCode {
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "연동된 계좌를 찾을 수 없습니다."),
    ACCOUNT_FORBIDDEN(HttpStatus.FORBIDDEN, "ACCOUNT_FORBIDDEN", "다른 사용자의 계좌에는 접근할 수 없습니다."),
    ACCOUNT_INSTITUTION_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "ACCOUNT_INSTITUTION_NOT_SUPPORTED", "지원하지 않는 금융기관입니다."),
    ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_LINKED", "이미 연동된 금융기관입니다."),
    ACCOUNT_ADDITIONAL_AUTH_UNSUPPORTED(HttpStatus.BAD_REQUEST, "ACCOUNT_ADDITIONAL_AUTH_UNSUPPORTED", "추가 인증이 필요한 금융기관은 아직 연동할 수 없습니다."),
    ACCOUNT_NO_ELIGIBLE_ACCOUNT(HttpStatus.BAD_REQUEST, "ACCOUNT_NO_ELIGIBLE_ACCOUNT", "연동할 수 있는 입출금 계좌가 없습니다."),
    ACCOUNT_LINK_REJECTED(HttpStatus.BAD_REQUEST, "ACCOUNT_LINK_REJECTED", "금융기관 인증에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
