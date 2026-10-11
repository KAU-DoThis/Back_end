package com.dothis.fintris.global.api.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralSuccessCode implements BaseSuccessCode { // 성공
    OK(HttpStatus.OK, "COMMON_200", "요청에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
