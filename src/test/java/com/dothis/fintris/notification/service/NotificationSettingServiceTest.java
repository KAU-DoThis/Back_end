package com.dothis.fintris.notification.service;

import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.notification.code.NotificationSettingErrorCode;
import com.dothis.fintris.notification.dto.NotificationSettingsBody;
import com.dothis.fintris.notification.dto.NotificationSettingsBody.BlockWarnings;
import com.dothis.fintris.notification.dto.NotificationSettingsBody.ContextQuestion;
import com.dothis.fintris.notification.dto.NotificationSettingsBody.News;
import com.dothis.fintris.notification.dto.NotificationSettingsBody.QuietHours;
import com.dothis.fintris.notification.entity.ContextFrequency;
import com.dothis.fintris.notification.entity.NotificationSetting;
import com.dothis.fintris.notification.repository.NotificationSettingRepository;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.Consent;
import com.dothis.fintris.user.entity.ConsentItem;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.ConsentRepository;
import com.dothis.fintris.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

    private static final Long USER_ID = 1L;

    @Mock NotificationSettingRepository notificationSettingRepository;
    @Mock ConsentRepository consentRepository;
    @Mock UserRepository userRepository;
    @InjectMocks NotificationSettingService service;

    private User user() {
        User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private void givenMarketing(boolean agreed) {
        Consent consent = Consent.builder().user(user()).item(ConsentItem.MARKETING).agreed(agreed).docVersion("v1").build();
        when(consentRepository.findFirstByUser_IdAndItemOrderByIdDesc(USER_ID, ConsentItem.MARKETING))
                .thenReturn(Optional.of(consent));
    }

    private NotificationSettingsBody request(boolean fixedShortage, boolean marketing, boolean urgentOffConfirmed) {
        return new NotificationSettingsBody(true,
                new ContextQuestion(true, ContextFrequency.DAILY_DIGEST),
                new QuietHours(LocalTime.of(22, 0), LocalTime.of(9, 0)),
                new BlockWarnings(true, fixedShortage, false),
                new News(true, marketing),
                LocalTime.of(8, 30), false, urgentOffConfirmed);
    }

    private GeneralException errorOf(Runnable call) {
        try {
            call.run();
        } catch (GeneralException e) {
            return e;
        }
        throw new AssertionError("예외가 나지 않았다");
    }

    @Test
    @DisplayName("저장한 적이 없으면 기본값(07~23시, 하루 3번, 긴급 켜짐)을 준다")
    void defaults() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user());

        NotificationSettingsBody settings = service.getSettings(USER_ID);

        assertThat(settings.pushEnabled()).isTrue();
        assertThat(settings.contextQuestion().frequency()).isEqualTo(ContextFrequency.MAX_3_PER_DAY);
        assertThat(settings.quietHours()).isEqualTo(new QuietHours(LocalTime.of(23, 0), LocalTime.of(7, 0)));
        assertThat(settings.blockWarnings().fixedShortage()).isTrue();
        assertThat(settings.news().marketing()).isFalse();
        assertThat(settings.dailyGuideTime()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("처음 수정하면 행을 만들고 보낸 값으로 바꾼다")
    void updateCreatesRow() {
        givenMarketing(true);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user());
        when(notificationSettingRepository.save(any(NotificationSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationSettingsBody updated = service.updateSettings(USER_ID, request(true, true, false));

        assertThat(updated.contextQuestion().frequency()).isEqualTo(ContextFrequency.DAILY_DIGEST);
        assertThat(updated.quietHours().start()).isEqualTo(LocalTime.of(22, 0));
        assertThat(updated.blockWarnings().goalBehind()).isFalse();
        assertThat(updated.news().marketing()).isTrue();
        assertThat(updated.dailyGuideTime()).isEqualTo(LocalTime.of(8, 30));
    }

    @Test
    @DisplayName("긴급 알림을 확인 없이 끄면 400")
    void urgentOffNeedsConfirm() {
        GeneralException e = errorOf(() -> service.updateSettings(USER_ID, request(false, false, false)));

        assertThat(e.getCode()).isEqualTo(NotificationSettingErrorCode.NOTIFICATION_URGENT_OFF_CONFIRM_REQUIRED);
        verify(notificationSettingRepository, never()).save(any());
    }

    @Test
    @DisplayName("확인하고 끄면 긴급 알림을 끈다")
    void urgentOffConfirmed() {
        givenMarketing(false);
        NotificationSetting existing = NotificationSetting.defaults(user());
        when(notificationSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(existing));

        NotificationSettingsBody updated = service.updateSettings(USER_ID, request(false, false, true));

        assertThat(updated.blockWarnings().fixedShortage()).isFalse();
        assertThat(updated.urgentOffConfirmed()).isTrue();
    }

    @Test
    @DisplayName("마케팅 동의 없이 혜택·소식 알림을 켜면 409")
    void marketingNeedsConsent() {
        givenMarketing(false);

        GeneralException e = errorOf(() -> service.updateSettings(USER_ID, request(true, true, false)));

        assertThat(e.getCode()).isEqualTo(NotificationSettingErrorCode.NOTIFICATION_MARKETING_CONSENT_REQUIRED);
    }

    @Test
    @DisplayName("켜 둔 뒤 마케팅 동의를 철회했으면 꺼진 것으로 보여 준다")
    void marketingHiddenAfterWithdrawal() {
        givenMarketing(false);
        NotificationSetting existing = NotificationSetting.defaults(user());
        existing.update(true, true, ContextFrequency.MAX_3_PER_DAY, LocalTime.of(23, 0), LocalTime.of(7, 0),
                true, true, true, true, true, LocalTime.of(8, 0), false, false);
        when(notificationSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(existing));

        assertThat(service.getSettings(USER_ID).news().marketing()).isFalse();
    }

    @Test
    @DisplayName("JSON은 명세 모양(BURN_RATE 같은 대문자 키, HH:mm 시각)으로 주고받는다")
    void jsonShape() {
        JsonMapper mapper = JsonMapper.builder().build();
        String json = mapper.writeValueAsString(request(true, false, false));

        assertThat(json).contains("\"BURN_RATE\":true", "\"SAVING_AND_REPORT\":true", "\"start\":\"22:00\"",
                "\"dailyGuideTime\":\"08:30\"");
        assertThat(mapper.readValue(json, NotificationSettingsBody.class)).isEqualTo(request(true, false, false));
    }
}
