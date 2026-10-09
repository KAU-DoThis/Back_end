package com.dothis.fintris.global.api.code;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode {

    /**
     * Provides the HTTP status associated with the error code.
     *
     * @return the associated HTTP status
     */
    HttpStatus getHttpStatus();
    /**
     * Provides the machine-readable code identifying the error.
     *
     * @return the error code
     */
    String getCode();
    /**
     * Provides the human-readable message associated with the error.
     *
     * @return the error message
     */
    String getMessage();

    /**
     * Creates a reason data transfer object from this error code's status, code, and message.
     *
     * @return the reason data transfer object containing this error code's details
     */
    default ReasonDTO getReason() {
        return ReasonDTO.builder()
                .httpStatus(getHttpStatus())
                .code(getCode())
                .message(getMessage())
                .build();
    }
}
