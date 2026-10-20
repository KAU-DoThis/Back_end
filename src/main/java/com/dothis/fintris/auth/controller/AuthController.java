package com.dothis.fintris.auth.controller;

import com.dothis.fintris.auth.dto.LoginRequest;
import com.dothis.fintris.auth.dto.RefreshTokenRequest;
import com.dothis.fintris.auth.dto.SignupRequest;
import com.dothis.fintris.auth.dto.TokenResponse;
import com.dothis.fintris.auth.service.AuthService;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "가입 및 로그인 (F-17)")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "이메일 가입", description = "가입과 동시에 토큰을 발급한다")
    @PostMapping("/signup")
    public ApiResponse<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, authService.signup(request));
    }

    @Operation(summary = "이메일 로그인", description = "5회 연속 실패하면 10분간 로그인할 수 없다")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, authService.login(request));
    }

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰은 한 번만 쓸 수 있고 새 토큰 쌍을 돌려준다")
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, authService.reissue(request.refreshToken()));
    }

    @Operation(summary = "로그아웃", description = "리프레시 토큰을 폐기한다")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}
