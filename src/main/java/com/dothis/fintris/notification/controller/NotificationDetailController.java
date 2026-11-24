package com.dothis.fintris.notification.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.notification.dto.BurnRateDetailResponse;
import com.dothis.fintris.notification.service.NotificationDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림함 (API 명세 v4 9장)")
@RestController
@RequiredArgsConstructor
public class NotificationDetailController {

    private final NotificationDetailService notificationDetailService;

    @Operation(summary = "이상 징후 상세 (13-1 소진 속도)",
            description = "지금은 BURN_RATE만 상세가 있다(그 밖은 400). 볼 때마다 지금 잔액·거래로 다시 계산한다. "
                    + "최근 = 오늘 포함 7일, 평소 = 그 앞 4주 같은 카테고리 하루 평균. 블록이 지워졌으면 404")
    @GetMapping("/notifications/{notificationId}")
    public ApiResponse<BurnRateDetailResponse> getDetail(@AuthenticationPrincipal Long userId,
                                                         @PathVariable Long notificationId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, notificationDetailService.getDetail(userId, notificationId));
    }
}
