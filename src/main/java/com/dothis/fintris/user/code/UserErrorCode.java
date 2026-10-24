package com.dothis.fintris.user.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorCode implements BaseErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "USER_REQUIRED_CONSENT_MISSING", "필수 동의 항목에 동의해야 합니다."),
    USER_CONSENT_ITEM_INVALID(HttpStatus.BAD_REQUEST, "USER_CONSENT_ITEM_INVALID", "존재하지 않는 동의 항목입니다."),
    USER_CONSENT_ITEM_DUPLICATED(HttpStatus.BAD_REQUEST, "USER_CONSENT_ITEM_DUPLICATED", "같은 동의 항목이 두 번 이상 포함되어 있습니다."),
    USER_CONSENT_DOC_VERSION_MISMATCH(HttpStatus.BAD_REQUEST, "USER_CONSENT_DOC_VERSION_MISMATCH", "최신 동의 문서 버전이 아닙니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
