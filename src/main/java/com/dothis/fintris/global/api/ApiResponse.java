package com.dothis.fintris.global.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.dothis.fintris.global.api.code.BaseErrorCode;
import com.dothis.fintris.global.api.code.BaseSuccessCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
@Schema(description = "공통 API 응답 래퍼")
public class ApiResponse<T> { // API 응답

    @Schema(description = "성공 여부", example = "true")
    private final Boolean isSuccess; // 성공 여부
    @Schema(description = "비즈니스 코드", example = "COMMON_200")
    private final String code; // 응답 코드
    @Schema(description = "메시지", example = "성공입니다.")
    private final String message; // 메세지
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "응답 데이터 (실패 시 보통 null)")
    private final T result; // 응답 데이터

    // HTTP 상태 코드는 바디에는 포함하지 않고, 성공 핸들러에서만 사용
    @JsonIgnore
    private final HttpStatus httpStatus;

    /**
     * Creates a successful API response containing the specified result.
     *
     * @param code   the success code defining the response code, message, and HTTP status
     * @param result the response data
     * @return       a successful API response with the specified result
     */
    public static <T> ApiResponse<T> onSuccess(BaseSuccessCode code, T result) {
        return new ApiResponse<>(
                true,
                code.getReason().getCode(),
                code.getReason().getMessage(),
                result,
                code.getReason().getHttpStatus()
        );
    }

    /**
     * Creates a failed API response without a result payload.
     *
     * @param code the error code defining the response code, message, and HTTP status
     * @return a failed response with no result data
     */
    public static ApiResponse<Void> onFailure(BaseErrorCode code) {
        return new ApiResponse<>(
                false,
                code.getReason().getCode(),
                code.getReason().getMessage(),
                null,
                code.getReason().getHttpStatus()
        );
    }

    /**
     * Creates a failure response containing the provided result data.
     *
     * @param code the error code and associated response details
     * @param data the result data to include in the response
     * @return a failure response with the specified error details and data
     */
    public static <T> ApiResponse<T> onFailure(BaseErrorCode code, T data) {
        return new ApiResponse<>(
                false,
                code.getReason().getCode(),
                code.getReason().getMessage(),
                data,
                code.getReason().getHttpStatus()
        );
    }
}