package com.dothis.fintris.user.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.user.dto.ConsentChangeRequest;
import com.dothis.fintris.user.dto.ConsentChangeResponse;
import com.dothis.fintris.user.dto.ConsentItemResponse;
import com.dothis.fintris.user.dto.ConsentSaveRequest;
import com.dothis.fintris.user.dto.ConsentSaveResponse;
import com.dothis.fintris.user.dto.ConsentStatusResponse;
import com.dothis.fintris.user.dto.UserSettingResponse;
import com.dothis.fintris.user.dto.UserSettingUpdateRequest;
import com.dothis.fintris.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// TODO: JWT 인증이 붙으면 X-User-Id 헤더 대신 로그인 사용자에서 userId를 꺼낸다
@Tag(name = "User", description = "동의 및 첫 사용 안내 (F-18)")
@RestController
@RequiredArgsConstructor
public class UserController {

    private static final String USER_HEADER = "X-User-Id";

    private final UserService userService;

    @Operation(summary = "동의 항목 목록", description = "로그인 전 동의 화면용. 인증 불필요")
    @GetMapping("/consents/items")
    public ApiResponse<List<ConsentItemResponse>> getConsentItems() {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, userService.getConsentItems());
    }

    @Operation(summary = "동의 저장",
            description = "여러 항목을 한 번에 저장한다. 저장 후 TERMS·PRIVACY가 동의 상태가 아니면 거부한다")
    @PostMapping("/users/me/consents")
    public ApiResponse<ConsentSaveResponse> saveConsents(
            @Parameter(description = "임시 사용자 ID") @RequestHeader(USER_HEADER) Long userId,
            @RequestBody @Valid ConsentSaveRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, userService.saveConsents(userId, request));
    }

    @Operation(summary = "내 동의 현황", description = "항목별 가장 최근 상태. 한 번도 저장하지 않은 항목은 빠진다")
    @GetMapping("/users/me/consents")
    public ApiResponse<List<ConsentStatusResponse>> getConsents(
            @Parameter(description = "임시 사용자 ID") @RequestHeader(USER_HEADER) Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, userService.getConsents(userId));
    }

    @Operation(summary = "동의 변경·철회",
            description = "필수 항목(ALWAYS·ON_LINK) 철회는 confirmed=true일 때만 저장한다. 아니면 영향만 돌려준다")
    @PatchMapping("/users/me/consents/{item}")
    public ApiResponse<ConsentChangeResponse> changeConsent(
            @Parameter(description = "임시 사용자 ID") @RequestHeader(USER_HEADER) Long userId,
            @Parameter(description = "동의 항목", example = "CODEF") @PathVariable String item,
            @RequestBody @Valid ConsentChangeRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, userService.changeConsent(userId, item, request));
    }

    @Operation(summary = "온보딩 상태 조회", description = "설정이 없으면 기본값으로 만들어 돌려준다")
    @GetMapping("/users/me/settings")
    public ApiResponse<UserSettingResponse> getSettings(
            @Parameter(description = "임시 사용자 ID") @RequestHeader(USER_HEADER) Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, userService.getSettings(userId));
    }

    @Operation(summary = "온보딩 상태 수정", description = "보낸 필드만 바뀐다")
    @PatchMapping("/users/me/settings")
    public ApiResponse<UserSettingResponse> updateSettings(
            @Parameter(description = "임시 사용자 ID") @RequestHeader(USER_HEADER) Long userId,
            @RequestBody @Valid UserSettingUpdateRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, userService.updateSettings(userId, request));
    }
}
