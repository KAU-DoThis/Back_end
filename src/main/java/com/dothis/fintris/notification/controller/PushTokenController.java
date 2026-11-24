package com.dothis.fintris.notification.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.notification.dto.PushTokenRequest;
import com.dothis.fintris.notification.service.PushTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PushToken", description = "푸시 토큰 (API 명세 v4 9장)")
@RestController
@RequestMapping("/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @Operation(summary = "푸시 토큰 등록", description = "앱을 켤 때마다 보낸다. 이미 있는 토큰이면 로그인한 사용자로 옮긴다")
    @PostMapping
    public ApiResponse<Void> register(@AuthenticationPrincipal Long userId, @RequestBody @Valid PushTokenRequest request) {
        pushTokenService.register(userId, request.token(), request.deviceId());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }

    @Operation(summary = "푸시 토큰 삭제", description = "로그아웃할 때 보낸다. Expo 토큰의 [ ]는 URL 인코딩해서 보낸다(%5B %5D). 없는 토큰이어도 200")
    @DeleteMapping("/{token}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable String token) {
        pushTokenService.delete(userId, token);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}
