package com.dothis.fintris.devtools;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum DevToolsErrorCode implements BaseErrorCode {
    DEV_BLOCKS_NOT_CONFIRMED(HttpStatus.CONFLICT, "DEV_BLOCKS_NOT_CONFIRMED", "블록을 먼저 확정해 주세요."),
    DEV_CONFIRMED_AT_IN_FUTURE(HttpStatus.BAD_REQUEST, "DEV_CONFIRMED_AT_IN_FUTURE", "확정 시각은 지금보다 과거여야 합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
