package com.dothis.fintris.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 발급 응답")
public record TokenResponse(
        @Schema(description = "액세스 토큰. Authorization: Bearer {accessToken}으로 보낸다")
        String accessToken,

        @Schema(description = "리프레시 토큰")
        String refreshToken
) {
}
