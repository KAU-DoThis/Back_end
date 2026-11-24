package com.dothis.fintris.notification.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.notification.dto.NotificationListResponse;
import com.dothis.fintris.notification.entity.NotificationCategory;
import com.dothis.fintris.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림함 (API 명세 v4 9장)")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    @Operation(summary = "알림함",
            description = "category: ALL(전체) · WARNING(경고) · QUESTION(질문) · NEWS(소식). 최신순으로 오늘·이번 주·이전으로 묶는다. "
                    + "counts는 탭과 관계없이 분류별 읽지 않은 수. size는 최대 100")
    @GetMapping
    public ApiResponse<NotificationListResponse> getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "ALL") NotificationCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, notificationService.getNotifications(
                userId, category, Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
    }

    @Operation(summary = "알림 읽음 처리", description = "이미 읽은 알림이면 그대로 둔다. 다른 사용자의 알림이면 403")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal Long userId, @PathVariable Long notificationId) {
        notificationService.markRead(userId, notificationId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}
