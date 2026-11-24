package com.dothis.fintris.notification.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.notification.dto.NotificationSettingsBody;
import com.dothis.fintris.notification.service.NotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "NotificationSetting", description = "알림 설정 (API 명세 v4 9장, 14 화면)")
@RestController
@RequestMapping("/notifications/settings")
@RequiredArgsConstructor
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    @Operation(summary = "알림 설정 조회", description = "저장한 적이 없으면 기본값을 준다")
    @GetMapping
    public ApiResponse<NotificationSettingsBody> getSettings(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, notificationSettingService.getSettings(userId));
    }

    @Operation(summary = "알림 설정 수정",
            description = "전체를 보낸다. FIXED_SHORTAGE를 끌 때 urgentOffConfirmed=false면 400, "
                    + "MARKETING 동의 없이 news.MARKETING을 켜면 409")
    @PutMapping
    public ApiResponse<NotificationSettingsBody> updateSettings(@AuthenticationPrincipal Long userId,
                                                                @RequestBody @Valid NotificationSettingsBody request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, notificationSettingService.updateSettings(userId, request));
    }
}
