package com.dothis.fintris.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

@Schema(description = "이메일 가입 요청")
public record SignupRequest(
        @Schema(description = "이메일", example = "user@example.com")
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(description = "비밀번호(8~64자, UTF-8 72바이트 이하)", example = "password1234")
        @NotBlank @Size(min = 8, max = 64) String password
) {

    // BCrypt는 72바이트까지만 처리하므로 한글처럼 멀티바이트 문자가 섞인 경우를 막는다
    @Schema(hidden = true)
    @AssertTrue(message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
