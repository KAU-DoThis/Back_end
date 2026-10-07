package com.dothis.fintris.global.api.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralErrorCode implements BaseErrorCode { // 실패
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 에러"),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_1", "존재하지 않는 회원입니다."),
    USER_NOT_FOUND_BY_EMAIL(HttpStatus.NOT_FOUND, "USER_404_2", "EMAIL이 존재하지 않는 회원입니다."),
    USER_NOT_FOUND_BY_USERNAME(HttpStatus.NOT_FOUND, "USER_404_3", "USERNAME이 존재하지 않는 회원입니다."),

    // Login
    WRONG_REFRESH_TOKEN(HttpStatus.NOT_FOUND, "JWT_404_1", "일치하는 refresh token이 없습니다."),
    IP_NOT_MATCHED(HttpStatus.FORBIDDEN, "JWT_403_1", "refresh token의 IP주소가 일치하지 않습니다."),
    TOKEN_INVALID(HttpStatus.FORBIDDEN, "JWT_403_2", "유효하지 않은 token입니다."),
    TOKEN_NO_AUTH(HttpStatus.FORBIDDEN, "JWT_403_3", "권한 정보가 없는 token입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "JWT_401_1", "token 유효기간이 만료되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
