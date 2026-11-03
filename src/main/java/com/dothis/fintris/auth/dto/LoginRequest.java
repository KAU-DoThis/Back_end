package com.dothis.fintris.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.nio.charset.StandardCharsets;

@Schema(description = "이메일 로그인 요청")
public record LoginRequest(
        @Schema(description = "이메일", example = "user@example.com")
        @NotBlank String email,

        @Schema(description = "비밀번호", example = "password1234")
        @NotBlank String password
) {

    // BCrypt는 72바이트까지만 처리하므로 초과 입력은 검증 단계에서 막는다
    @Schema(hidden = true)
    @AssertTrue(message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
