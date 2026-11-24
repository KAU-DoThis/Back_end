package com.dothis.fintris.notification.service;

import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.notification.code.NotificationSettingErrorCode;
import com.dothis.fintris.notification.dto.NotificationSettingsBody;
import com.dothis.fintris.notification.entity.NotificationSetting;
import com.dothis.fintris.notification.repository.NotificationSettingRepository;
import com.dothis.fintris.user.entity.Consent;
import com.dothis.fintris.user.entity.ConsentItem;
import com.dothis.fintris.user.repository.ConsentRepository;
import com.dothis.fintris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 알림 설정 조회·수정 (v4 9장, F-22)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final ConsentRepository consentRepository;
    private final UserRepository userRepository;

    public NotificationSettingsBody getSettings(Long userId) {
        NotificationSetting setting = notificationSettingRepository.findByUser_Id(userId)
                .orElseGet(() -> NotificationSetting.defaults(userRepository.getReferenceById(userId)));
        return NotificationSettingsBody.of(setting, marketingAgreed(userId));
    }

    @Transactional
    public NotificationSettingsBody updateSettings(Long userId, NotificationSettingsBody request) {
        // 긴급 알림(고정지출 납부 부족)은 끌 수 있지만 한 번 확인받는다 (F-22)
        if (!request.blockWarnings().fixedShortage() && !request.urgentOffConfirmed()) {
            throw GeneralException.of(NotificationSettingErrorCode.NOTIFICATION_URGENT_OFF_CONFIRM_REQUIRED);
        }
        boolean marketingAgreed = marketingAgreed(userId);
        if (request.news().marketing() && !marketingAgreed) {
            throw GeneralException.of(NotificationSettingErrorCode.NOTIFICATION_MARKETING_CONSENT_REQUIRED);
        }

        NotificationSetting setting = notificationSettingRepository.findByUser_Id(userId)
                .orElseGet(() -> notificationSettingRepository.save(
                        NotificationSetting.defaults(userRepository.getReferenceById(userId))));
        setting.update(request.pushEnabled(), request.contextQuestion().enabled(), request.contextQuestion().frequency(),
                request.quietHours().start(), request.quietHours().end(), request.blockWarnings().burnRate(),
                request.blockWarnings().fixedShortage(), request.blockWarnings().goalBehind(),
                request.news().savingAndReport(), request.news().marketing(), request.dailyGuideTime(),
                request.lockScreenHideAmount(), request.urgentOffConfirmed());
        return NotificationSettingsBody.of(setting, marketingAgreed);
    }

    private boolean marketingAgreed(Long userId) {
        return consentRepository.findFirstByUser_IdAndItemOrderByIdDesc(userId, ConsentItem.MARKETING)
                .map(Consent::isAgreed)
                .orElse(false);
    }
}
