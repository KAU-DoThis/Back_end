package com.dothis.fintris.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "푸시 토큰 등록")
public record PushTokenRequest(
        @Schema(description = "Expo push token", example = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]")
        @NotBlank @Size(max = 255) String token,
        @Schema(description = "선택", example = "iPhone15-ABCD") @Size(max = 100) String deviceId
) {
}
