package com.dothis.fintris.notification.dto;

import com.dothis.fintris.notification.entity.Notification;
import com.dothis.fintris.notification.entity.NotificationCategory;
import com.dothis.fintris.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// API 명세 v4 9장 알림함. 하루가 두 페이지에 걸치면 FE가 같은 label끼리 이어 붙인다
@Schema(description = "알림함")
public record NotificationListResponse(
        @Schema(description = "탭별 읽지 않은 알림 수 (WARNING·QUESTION·NEWS)", example = "{\"WARNING\": 2, \"QUESTION\": 1, \"NEWS\": 0}")
        Map<NotificationCategory, Long> counts,
        @Schema(description = "최신순. 오늘 · 이번 주 · 이전") List<Group> groups,
        @Schema(description = "이 기간이 지난 알림은 지운다", example = "30") int retentionDays,
        boolean hasNext
) {

    public record Group(
            @Schema(example = "오늘") String label,
            List<Item> items
    ) {
    }

    public record Item(
            @Schema(example = "301") Long notificationId,
            @Schema(example = "WARNING") NotificationCategory category,
            @Schema(example = "BURN_RATE") NotificationType type,
            @Schema(description = "긴급 알림 (고정지출 납부 부족)", example = "false") boolean urgent,
            @Schema(example = "생활비가 평소보다 17% 빨리 줄고 있어요") String title,
            @Schema(example = "최근 7일 하루 평균 68,000원. 이 속도면 28일쯤 바닥나요") String body,
            @Schema(description = "경고 알림만", example = "확인 필요") String badge,
            @Schema(description = "눌렀을 때 열 대상 ID. CONTEXT_QUESTION이면 questionId, 없으면 null", example = "56") Long refId,
            boolean read,
            @Schema(example = "2026-10-14T09:00:00") LocalDateTime createdAt
    ) {

        public static Item from(Notification notification) {
            NotificationType type = notification.getType();
            return new Item(notification.getId(), type.getCategory(), type, type.isUrgent(),
                    notification.getTitle(), notification.getBody(),
                    type.getCategory() == NotificationCategory.WARNING ? "확인 필요" : null,
                    notification.getRefId(), notification.isRead(), notification.getCreatedAt());
        }
    }
}
