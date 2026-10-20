package com.dothis.fintris.user.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorCode implements BaseErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "USER_REQUIRED_CONSENT_MISSING", "필수 동의 항목에 동의해야 합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
