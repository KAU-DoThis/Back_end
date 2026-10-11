package com.dothis.fintris.global.exception;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import com.dothis.fintris.global.api.code.ReasonDTO;
import lombok.Getter;

// 공통 예외 처리
@Getter
public class GeneralException extends RuntimeException {

  private final BaseErrorCode code;

  public GeneralException(BaseErrorCode code) {
    super(code.getMessage());
    this.code = code;
  }

  public GeneralException(BaseErrorCode code, Throwable cause) {
    super(code.getMessage(), cause);
    this.code = code;
  }

  /**
   * Creates an exception associated with the specified error code.
   *
   * @param code the error code associated with the exception
   * @return a new exception containing the specified error code
   */
  public static GeneralException of(BaseErrorCode code) {
    return new GeneralException(code);
  }

  /**
   * Creates an exception associated with the specified error code and cause.
   *
   * @param code the error code associated with the exception
   * @param cause the underlying cause
   * @return a new exception containing the specified error code and cause
   */
  public static GeneralException of(BaseErrorCode code, Throwable cause) {
    return new GeneralException(code, cause);
  }

  /**
   * Provides detailed information about the exception's error code.
   *
   * @return the reason associated with the error code
   */
  public ReasonDTO getReason() {
    return this.code.getReason();
  }
}
