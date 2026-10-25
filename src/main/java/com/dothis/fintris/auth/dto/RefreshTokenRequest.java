package com.dothis.fintris.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "리프레시 토큰 요청")
public record RefreshTokenRequest(
        @Schema(description = "리프레시 토큰")
        @NotBlank String refreshToken
) {
}
