package com.dothis.fintris.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카카오 로그인 요청")
public record KakaoLoginRequest(
        @Schema(description = "카카오 SDK로 받은 액세스 토큰")
        @NotBlank String accessToken
) {
}
