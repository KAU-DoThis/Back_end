package com.dothis.fintris.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "금융기관 연동 요청 (아이디·비밀번호 로그인)")
public record LinkedAccountCreateRequest(
        @Schema(description = "CODEF 기관코드", example = "0004")
        @NotBlank @Size(max = 10) String institutionCode,

        @Schema(description = "금융기관 로그인 아이디", example = "codef")
        @NotBlank @Size(max = 100) String loginId,

        @Schema(description = "금융기관 로그인 비밀번호. 저장하지 않고 CODEF로만 전달한다")
        @NotBlank @Size(max = 100) String password,

        @Schema(description = "생년월일 6자리(YYMMDD)", example = "980225")
        @NotBlank @Pattern(regexp = "\\d{6}", message = "생년월일은 숫자 6자리여야 합니다.") String birthDate
) {

    // 로그에 인증정보가 남지 않도록 막는다
    @Override
    public String toString() {
        return "LinkedAccountCreateRequest[institutionCode=" + institutionCode + "]";
    }
}
