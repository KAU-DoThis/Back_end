package com.dothis.fintris.global.api.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

// 도메인 공통 실패 코드
// 도메인별 코드는 각 도메인 패키지에 BaseErrorCode를 구현한 enum으로 정의한다. (예: BlockErrorCode)
// code는 상수명과 같은 `도메인_원인` 형식으로 쓴다. (예: BLOCK_NOT_FOUND)
@Getter
@AllArgsConstructor
public enum GeneralErrorCode implements BaseErrorCode { // 실패
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_BAD_REQUEST", "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_CONFLICT", "데이터 제약 조건에 위배되는 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_SERVER_ERROR", "서버 에러");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
