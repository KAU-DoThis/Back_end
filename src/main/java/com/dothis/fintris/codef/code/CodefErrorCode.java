package com.dothis.fintris.codef.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CodefErrorCode implements BaseErrorCode {
    CODEF_TOKEN_ISSUE_FAILED(HttpStatus.BAD_GATEWAY, "CODEF_TOKEN_ISSUE_FAILED", "CODEF 액세스 토큰을 발급받지 못했습니다."),
    CODEF_API_FAILED(HttpStatus.BAD_GATEWAY, "CODEF_API_FAILED", "CODEF API 호출에 실패했습니다."),
    CODEF_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "CODEF_API_TIMEOUT", "CODEF 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
