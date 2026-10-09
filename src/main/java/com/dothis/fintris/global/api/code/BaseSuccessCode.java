package com.dothis.fintris.global.api.code;

import org.springframework.http.HttpStatus;

public interface BaseSuccessCode {

    /**
     * Provides the HTTP status associated with this success code.
     *
     * @return the associated HTTP status
     */
    HttpStatus getHttpStatus();
    /**
     * Provides the machine-readable code identifier.
     *
     * @return the code identifier
     */
    String getCode();
    /**
     * Provides the human-readable message for the success code.
     *
     * @return the success message
     */
    String getMessage();

    /**
     * Creates a reason object from the success code's HTTP status, code, and message.
     *
     * @return the corresponding reason object
     */
    default ReasonDTO getReason() {
        return ReasonDTO.builder()
                .httpStatus(getHttpStatus())
                .code(getCode())
                .message(getMessage())
                .build();
    }
}
