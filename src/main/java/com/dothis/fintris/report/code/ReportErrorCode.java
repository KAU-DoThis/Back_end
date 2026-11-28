package com.dothis.fintris.report.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ReportErrorCode implements BaseErrorCode {
    REPORT_FUTURE_MONTH(HttpStatus.BAD_REQUEST, "REPORT_FUTURE_MONTH", "아직 오지 않은 달의 리포트는 볼 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
