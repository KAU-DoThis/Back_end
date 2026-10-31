package com.dothis.fintris.global.api.code;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@Builder
public class ReasonDTO {

    private HttpStatus httpStatus; // HTTP 상태 코드
    private String code;
    private String message;
}
