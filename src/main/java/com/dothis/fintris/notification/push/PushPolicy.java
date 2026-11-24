package com.dothis.fintris.notification.push;

import com.dothis.fintris.notification.entity.ContextFrequency;
import com.dothis.fintris.notification.entity.NotificationSetting;
import com.dothis.fintris.notification.entity.NotificationType;

import java.time.LocalTime;

// 알림을 지금 푸시로 보낼지 정한다 (기능명세서 F-22, F-15). 알림함에는 어떤 경우든 남아 있다
// - 긴급(고정지출 납부 부족)은 받지 않는 시간·하루 건수와 관계없이 보낸다
// - 받지 않는 시간이면 미뤘다가 끝나는 시각에 보낸다. 하루 건수를 넘으면 알림함에만 둔다
// - 맥락 질문은 빈도 설정을 따른다 (하루 3번까지 / 가져올 때마다 / 저녁 9시에 모아서)
public final class PushPolicy {

    static final int DAILY_MAX = 5;           // F-22 하루 최대 5건
    static final int CONTEXT_DAILY_MAX = 3;   // 맥락 질문은 3건
    static final LocalTime DIGEST_TIME = LocalTime.of(21, 0);
    static final String HIDDEN_TEXT = "확인할 알림이 있어요";

    private PushPolicy() {
    }

    public enum Decision {
        SEND,  // 지금 보낸다
        HOLD,  // 받지 않는 시간이라 미룬다 (맥락 질문 모아 보내기 포함)
        SKIP   // 보내지 않는다 (알림함에만)
    }

    // sentToday: 오늘 이미 보낸 일반 알림 수, contextSentToday: 그중 맥락 질문 수
    public static Decision decide(NotificationType type, NotificationSetting setting, LocalTime now,
                                  int sentToday, int contextSentToday) {
        if (!setting.isPushEnabled() || !typeEnabled(type, setting)) {
            return Decision.SKIP;
        }
        if (type.isUrgent()) {
            return Decision.SEND;
        }
        if (type == NotificationType.CONTEXT_QUESTION && setting.getContextFrequency() == ContextFrequency.DAILY_DIGEST
                && now.isBefore(DIGEST_TIME)) {
            return Decision.HOLD;
        }
        if (inQuietHours(now, setting.getQuietStart(), setting.getQuietEnd())) {
            return Decision.HOLD;
        }
        if (sentToday >= DAILY_MAX) {
            return Decision.SKIP;
        }
        if (type == NotificationType.CONTEXT_QUESTION && setting.getContextFrequency() == ContextFrequency.MAX_3_PER_DAY
                && contextSentToday >= CONTEXT_DAILY_MAX) {
            return Decision.SKIP;
        }
        return Decision.SEND;
    }

    // 설정 화면의 켜기·끄기. 토글이 없는 종류는 켜져 있다고 본다
    static boolean typeEnabled(NotificationType type, NotificationSetting setting) {
        return switch (type) {
            case CONTEXT_QUESTION -> setting.isContextEnabled();
            case BURN_RATE -> setting.isBurnRateEnabled();
            case FIXED_SHORTAGE -> setting.isFixedShortageEnabled();
            case GOAL_BEHIND -> setting.isGoalBehindEnabled();
            case SAVING_CONFIRMED, GOAL_ACHIEVED, GOAL_EXPIRED, REPORT -> setting.isSavingReportEnabled();
            default -> true;
        };
    }

    // 23:00~07:00처럼 자정을 넘는 구간도 있다. 시작은 포함, 끝은 포함하지 않는다
    static boolean inQuietHours(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    // 잠금 화면 금액 숨김을 켜면 제목·본문 대신 이 문장만 보낸다
    public static String title(String title, NotificationSetting setting) {
        return setting.isLockScreenHideAmount() ? HIDDEN_TEXT : title;
    }

    public static String body(String body, NotificationSetting setting) {
        return setting.isLockScreenHideAmount() ? null : body;
    }
}
