package com.dothis.fintris.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카카오 로그인 응답")
public record KakaoLoginResponse(
        @Schema(description = "액세스 토큰")
        String accessToken,

        @Schema(description = "리프레시 토큰")
        String refreshToken,

        @Schema(description = "신규 가입 여부. true면 온보딩(F-18)으로 이동한다")
        boolean isNewUser
) {
}
