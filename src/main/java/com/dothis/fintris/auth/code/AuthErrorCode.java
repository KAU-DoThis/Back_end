package com.dothis.fintris.auth.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "인증이 필요합니다."),
    AUTH_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "AUTH_EMAIL_DUPLICATED", "이미 가입된 이메일입니다."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_ACCOUNT_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_ACCOUNT_LOCKED", "로그인 시도가 5회 연속 실패해 10분간 로그인할 수 없습니다."),
    AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_REFRESH_TOKEN", "유효하지 않은 리프레시 토큰입니다."),
    AUTH_KAKAO_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_KAKAO_UNAUTHORIZED", "유효하지 않은 카카오 토큰입니다."),
    AUTH_KAKAO_API_FAILED(HttpStatus.BAD_GATEWAY, "AUTH_KAKAO_API_FAILED", "카카오 서버 응답에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
