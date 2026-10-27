package com.dothis.fintris.account.exception;

import com.dothis.fintris.global.api.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// 공통 핸들러보다 먼저 동작해 기관 거절 사유를 result에 담는다
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccountExceptionHandler {

    @ExceptionHandler(AccountLinkRejectedException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> onLinkRejected(AccountLinkRejectedException e) {
        Map<String, String> result = e.getInstitutionMessage() == null
                ? Map.of() : Map.of("reason", e.getInstitutionMessage());
        return ResponseEntity.status(e.getCode().getReason().getHttpStatus())
                .body(ApiResponse.onFailure(e.getCode(), result));
    }
}
