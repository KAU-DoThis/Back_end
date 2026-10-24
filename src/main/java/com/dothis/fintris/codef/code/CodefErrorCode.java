package com.dothis.fintris.codef.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CodefErrorCode implements BaseErrorCode {
    CODEF_TOKEN_ISSUE_FAILED(HttpStatus.BAD_GATEWAY, "CODEF_TOKEN_ISSUE_FAILED", "CODEF 액세스 토큰을 발급받지 못했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
