package com.dothis.fintris.notification.push;

import com.dothis.fintris.notification.entity.ContextFrequency;
import com.dothis.fintris.notification.entity.Notification;
import com.dothis.fintris.notification.entity.NotificationSetting;
import com.dothis.fintris.notification.entity.NotificationType;
import com.dothis.fintris.notification.entity.PushStatus;
import com.dothis.fintris.notification.entity.PushToken;
import com.dothis.fintris.notification.push.ExpoPushClient.PushResult;
import com.dothis.fintris.notification.repository.NotificationLogRepository;
import com.dothis.fintris.notification.repository.NotificationRepository;
import com.dothis.fintris.notification.repository.NotificationSettingRepository;
import com.dothis.fintris.notification.repository.PushTokenRepository;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushDispatcherTest {

    private static final Long USER_ID = 1L;

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationSettingRepository notificationSettingRepository;
    @Mock PushTokenRepository pushTokenRepository;
    @Mock NotificationLogRepository notificationLogRepository;
    @Mock ExpoPushClient expoPushClient;
    @InjectMocks PushDispatcher dispatcher;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    private Notification given(NotificationType type) {
        Notification notification = Notification.builder().user(user).type(type)
                .title("생활비가 평소보다 17% 빨리 줄고 있어요").body("최근 7일 하루 평균 68,000원").refId(10L).build();
        ReflectionTestUtils.setField(notification, "id", 301L);
        when(notificationRepository.findById(301L)).thenReturn(Optional.of(notification));
        return notification;
    }

    // 하루 종일 받는 설정이라 지금 시각과 관계없이 받는 시간이다
    private void givenAlwaysOpen() {
        NotificationSetting setting = NotificationSetting.defaults(user);
        setting.update(true, true, ContextFrequency.MAX_3_PER_DAY, LocalTime.of(0, 0), LocalTime.of(0, 0),
                true, true, true, true, false, LocalTime.of(8, 0), false, false);
        when(notificationSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(setting));
    }

    private PushToken token(String value) {
        return PushToken.builder().user(user).token(value).build();
    }

    @Test
    @DisplayName("보내면 기기마다 기록을 남기고, 지워진 기기의 토큰은 지운다")
    void sendsAndCleansUnregistered() {
        Notification notification = given(NotificationType.BURN_RATE);
        givenAlwaysOpen();
        PushToken phone = token("ExponentPushToken[phone]");
        PushToken old = token("ExponentPushToken[old]");
        when(pushTokenRepository.findAllByUser_Id(USER_ID)).thenReturn(List.of(phone, old));
        when(expoPushClient.send(any())).thenReturn(List.of(PushResult.SENT, PushResult.UNREGISTERED));

        dispatcher.dispatch(301L);

        assertThat(notification.getPushStatus()).isEqualTo(PushStatus.SENT);
        assertThat(notification.getPushedAt()).isNotNull();
        verify(pushTokenRepository).delete(old);
        verify(pushTokenRepository, never()).delete(phone);
        ArgumentCaptor<List<PushMessage>> messages = ArgumentCaptor.captor();
        verify(expoPushClient).send(messages.capture());
        assertThat(messages.getValue().getFirst().data()).containsEntry("type", "BURN_RATE").containsEntry("refId", 10L);
    }

    @Test
    @DisplayName("모든 기기에서 실패하면 FAILED")
    void allFailed() {
        Notification notification = given(NotificationType.BURN_RATE);
        givenAlwaysOpen();
        when(pushTokenRepository.findAllByUser_Id(USER_ID)).thenReturn(List.of(token("ExponentPushToken[a]")));
        when(expoPushClient.send(any())).thenReturn(List.of(PushResult.FAILED));

        dispatcher.dispatch(301L);

        assertThat(notification.getPushStatus()).isEqualTo(PushStatus.FAILED);
    }

    @Test
    @DisplayName("등록된 기기가 없으면 보내지 않는다")
    void noTokens() {
        Notification notification = given(NotificationType.BURN_RATE);
        givenAlwaysOpen();

        dispatcher.dispatch(301L);

        assertThat(notification.getPushStatus()).isEqualTo(PushStatus.SKIPPED);
        verify(expoPushClient, never()).send(any());
    }

    @Test
    @DisplayName("이미 보낸 알림은 다시 보내지 않는다")
    void alreadySent() {
        Notification notification = given(NotificationType.BURN_RATE);
        notification.pushed(PushStatus.SENT, LocalDateTime.now());

        dispatcher.dispatch(301L);

        verify(expoPushClient, never()).send(any());
    }
}
