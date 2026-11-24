package com.dothis.fintris.notification.push;

import com.dothis.fintris.notification.entity.ContextFrequency;
import com.dothis.fintris.notification.entity.NotificationSetting;
import com.dothis.fintris.notification.entity.NotificationType;
import com.dothis.fintris.notification.push.PushPolicy.Decision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class PushPolicyTest {

    private static final LocalTime NOON = LocalTime.of(12, 0);
    private static final LocalTime MIDNIGHT = LocalTime.of(0, 30);

    private NotificationSetting setting(boolean push, ContextFrequency frequency, boolean burnRate, boolean hideAmount) {
        NotificationSetting setting = NotificationSetting.builder().build();
        setting.update(push, true, frequency, LocalTime.of(23, 0), LocalTime.of(7, 0),
                burnRate, true, true, true, false, LocalTime.of(8, 0), hideAmount, false);
        return setting;
    }

    private NotificationSetting defaults() {
        return setting(true, ContextFrequency.MAX_3_PER_DAY, true, false);
    }

    @Test
    @DisplayName("받는 시간이고 건수가 남았으면 보낸다")
    void sendsDuringDay() {
        assertThat(PushPolicy.decide(NotificationType.BURN_RATE, defaults(), NOON, 0, 0)).isEqualTo(Decision.SEND);
    }

    @Test
    @DisplayName("받지 않는 시간(자정 넘는 구간)이면 미루고, 긴급은 밤에도 보낸다")
    void quietHoursHoldButUrgentSends() {
        assertThat(PushPolicy.decide(NotificationType.BURN_RATE, defaults(), MIDNIGHT, 0, 0)).isEqualTo(Decision.HOLD);
        assertThat(PushPolicy.decide(NotificationType.BURN_RATE, defaults(), LocalTime.of(23, 0), 0, 0)).isEqualTo(Decision.HOLD);
        assertThat(PushPolicy.decide(NotificationType.BURN_RATE, defaults(), LocalTime.of(7, 0), 0, 0)).isEqualTo(Decision.SEND);
        assertThat(PushPolicy.decide(NotificationType.FIXED_SHORTAGE, defaults(), MIDNIGHT, 9, 0)).isEqualTo(Decision.SEND);
    }

    @Test
    @DisplayName("하루 5건을 넘으면 알림함에만 두고, 맥락 질문은 3건까지")
    void dailyLimits() {
        assertThat(PushPolicy.decide(NotificationType.REPORT, defaults(), NOON, 5, 0)).isEqualTo(Decision.SKIP);
        assertThat(PushPolicy.decide(NotificationType.CONTEXT_QUESTION, defaults(), NOON, 3, 3)).isEqualTo(Decision.SKIP);
        assertThat(PushPolicy.decide(NotificationType.CONTEXT_QUESTION,
                setting(true, ContextFrequency.EACH_SYNC, true, false), NOON, 3, 3)).isEqualTo(Decision.SEND);
    }

    @Test
    @DisplayName("맥락 질문을 모아 받기로 했으면 저녁 9시까지 미룬다")
    void digestHoldsUntilNine() {
        NotificationSetting digest = setting(true, ContextFrequency.DAILY_DIGEST, true, false);
        assertThat(PushPolicy.decide(NotificationType.CONTEXT_QUESTION, digest, NOON, 0, 0)).isEqualTo(Decision.HOLD);
        assertThat(PushPolicy.decide(NotificationType.CONTEXT_QUESTION, digest, LocalTime.of(21, 0), 0, 0)).isEqualTo(Decision.SEND);
    }

    @Test
    @DisplayName("푸시를 끄거나 종류를 끄면 보내지 않는다")
    void turnedOff() {
        assertThat(PushPolicy.decide(NotificationType.BURN_RATE,
                setting(false, ContextFrequency.MAX_3_PER_DAY, true, false), NOON, 0, 0)).isEqualTo(Decision.SKIP);
        assertThat(PushPolicy.decide(NotificationType.BURN_RATE,
                setting(true, ContextFrequency.MAX_3_PER_DAY, false, false), NOON, 0, 0)).isEqualTo(Decision.SKIP);
    }

    @Test
    @DisplayName("잠금 화면 금액 숨김을 켜면 '확인할 알림이 있어요'만 보낸다")
    void hideAmount() {
        NotificationSetting hidden = setting(true, ContextFrequency.MAX_3_PER_DAY, true, true);
        assertThat(PushPolicy.title("생활비가 평소보다 17% 빨리 줄고 있어요", hidden)).isEqualTo("확인할 알림이 있어요");
        assertThat(PushPolicy.body("최근 7일 하루 평균 68,000원", hidden)).isNull();
        assertThat(PushPolicy.title("9월 리포트가 나왔어요", defaults())).isEqualTo("9월 리포트가 나왔어요");
    }
}
