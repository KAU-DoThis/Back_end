package com.dothis.fintris.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.BaseErrorCode;
import com.dothis.fintris.global.api.code.GeneralErrorCode;
import com.dothis.fintris.global.api.code.ReasonDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// 전역 예외 처리
@RestControllerAdvice(annotations = {RestController.class})
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handles constraint violations by returning a bad-request response.
     *
     * @param e       the constraint violation exception
     * @param request the current web request
     * @return        the standardized bad-request response
     */
    @ExceptionHandler
    public ResponseEntity<Object> validation(ConstraintViolationException e, WebRequest request) {
        String errorMessage = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("ConstraintViolationException 처리 중 에러 발생");
        return handleExceptionInternalConstraint(e, GeneralErrorCode.BAD_REQUEST, HttpHeaders.EMPTY, request);
    }

    /**
     * Handles a general application exception and creates a failure response using its error code.
     *
     * @param generalException the application exception containing the error code
     * @param request          the HTTP request associated with the exception
     * @return                the error response for the exception
     */
    @ExceptionHandler(value = GeneralException.class)
    public ResponseEntity<Object> onThrowException(GeneralException generalException,
                                                   HttpServletRequest request) {
        return handleExceptionInternal(generalException, generalException.getCode(), null, request);
    }

    /**
     * Handles database integrity violations by returning a bad-request response.
     *
     * @param e       the database integrity violation
     * @param request the HTTP request associated with the exception
     * @return a failure response with the applicable error code
     */
    @ExceptionHandler(value = DataIntegrityViolationException.class)
    public ResponseEntity<Object> onDataIntegrityViolationException(DataIntegrityViolationException e,
                                                                    HttpServletRequest request) {
        return handleExceptionInternal(e, GeneralErrorCode.BAD_REQUEST, null, request);
    }

    /**
     * Builds a standardized validation response containing field-specific errors.
     *
     * @param e       the exception containing field validation errors
     * @param headers the response headers
     * @param status  the HTTP status resolved for the exception
     * @param request the current web request
     * @return        a response containing the validation errors and applicable error code
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fieldError -> {
            String fieldName = fieldError.getField();
            String errorMessage = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("");
            errors.put(fieldName, errorMessage);
        });
        BaseErrorCode errorCode = GeneralErrorCode.BAD_REQUEST;
        return handleExceptionInternalArgs(e, HttpHeaders.EMPTY, errorCode, request, errors);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return handleExceptionInternalConstraint(e, GeneralErrorCode.BAD_REQUEST, headers, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException e,
            WebRequest request
    ) {
        return handleExceptionInternalConstraint(e, GeneralErrorCode.BAD_REQUEST, HttpHeaders.EMPTY, request);
    }

    /**
     * Handles uncaught exceptions by returning an internal server error response.
     *
     * @param e       the uncaught exception
     * @param request the current web request
     * @return        the internal server error response containing the exception message
     */
    @ExceptionHandler
    public ResponseEntity<Object> exception(Exception e, WebRequest request) {
        e.printStackTrace();
        return handleExceptionInternalFalse(e, GeneralErrorCode.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY,
                GeneralErrorCode.INTERNAL_SERVER_ERROR.getReason().getHttpStatus(), request, e.getMessage());
    }

    /**
     * Builds a standardized error response for the specified error code and request.
     *
     * @param e       the exception being handled
     * @param code    the error code describing the failure
     * @param headers the response headers
     * @param request the originating HTTP request
     * @return the standardized error response
     */
    private ResponseEntity<Object> handleExceptionInternal(Exception e, BaseErrorCode code,
                                                           HttpHeaders headers, HttpServletRequest request) {
        ReasonDTO reason = code.getReason();
        ApiResponse<Void> body = ApiResponse.onFailure(code);
        WebRequest webRequest = new ServletWebRequest(request);
        return super.handleExceptionInternal(e, body, headers, reason.getHttpStatus(), webRequest);
    }

    /**
     * Builds a failure response containing the specified error detail.
     *
     * @param e          the exception being handled
     * @param errorCode  the error code for the response
     * @param headers    the response headers
     * @param status     the HTTP status for the response
     * @param request    the current web request
     * @param errorPoint additional error detail included in the response
     * @return the constructed error response
     */
    private ResponseEntity<Object> handleExceptionInternalFalse(Exception e, BaseErrorCode errorCode,
                                                                HttpHeaders headers, HttpStatus status, WebRequest request,
                                                                String errorPoint) {
        ApiResponse<Object> body = ApiResponse.onFailure(errorCode, errorPoint);
        return super.handleExceptionInternal(e, body, headers, status, request);
    }

    /**
     * Builds a failure response containing field-specific error details.
     *
     * @param e         the exception being handled
     * @param headers   the response headers
     * @param errorCode the error code for the response
     * @param request   the current web request
     * @param errorArgs field-specific error messages
     * @return          the constructed error response
     */
    private ResponseEntity<Object> handleExceptionInternalArgs(Exception e, HttpHeaders headers,
                                                               BaseErrorCode errorCode, WebRequest request,
                                                               Map<String, String> errorArgs) {
        ApiResponse<Object> body = ApiResponse.onFailure(errorCode, errorArgs);
        return super.handleExceptionInternal(e, body, headers, errorCode.getReason().getHttpStatus(), request);
    }

    /**
     * Builds a failure response for a constraint violation.
     *
     * @param e         the handled exception
     * @param errorCode the error code associated with the failure
     * @param headers   the response headers
     * @param request   the current web request
     * @return          the generated failure response
     */
    private ResponseEntity<Object> handleExceptionInternalConstraint(Exception e, BaseErrorCode errorCode,
                                                                     HttpHeaders headers, WebRequest request) {
        ApiResponse<Void> body = ApiResponse.onFailure(errorCode);
        return super.handleExceptionInternal(e, body, headers, errorCode.getReason().getHttpStatus(), request);
    }
}
