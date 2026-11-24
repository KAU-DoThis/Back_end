package com.dothis.fintris.notification.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 알림 종류 (v4 9장). 분류와 긴급 여부는 종류로 정한다
@Getter
@AllArgsConstructor
public enum NotificationType {
    CONTEXT_QUESTION(NotificationCategory.QUESTION, false),
    DAILY_GUIDE(NotificationCategory.NEWS, false),
    FIXED_SHORTAGE(NotificationCategory.WARNING, true), // 고정지출 납부 부족은 긴급 (F-22)
    BURN_RATE(NotificationCategory.WARNING, false),
    MONTH_END_SHORTAGE(NotificationCategory.WARNING, false),
    GOAL_BEHIND(NotificationCategory.WARNING, false),
    OVER_ALLOCATED(NotificationCategory.WARNING, false),
    SAVING_CONFIRMED(NotificationCategory.NEWS, false),
    GOAL_ACHIEVED(NotificationCategory.NEWS, false),
    GOAL_EXPIRED(NotificationCategory.NEWS, false),
    REGULAR_INCOME(NotificationCategory.NEWS, false),
    EXTRA_INCOME(NotificationCategory.NEWS, false),
    REPORT(NotificationCategory.NEWS, false);

    private final NotificationCategory category;
    private final boolean urgent;
}
