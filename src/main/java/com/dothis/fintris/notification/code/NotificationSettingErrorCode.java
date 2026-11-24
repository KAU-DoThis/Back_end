package com.dothis.fintris.notification.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NotificationSettingErrorCode implements BaseErrorCode {
    NOTIFICATION_URGENT_OFF_CONFIRM_REQUIRED(HttpStatus.BAD_REQUEST, "NOTIFICATION_URGENT_OFF_CONFIRM_REQUIRED",
            "고정지출 납부 부족 경고를 끄면 받지 못합니다. 확인 후 urgentOffConfirmed=true로 다시 보내 주세요."),
    NOTIFICATION_MARKETING_CONSENT_REQUIRED(HttpStatus.CONFLICT, "NOTIFICATION_MARKETING_CONSENT_REQUIRED",
            "혜택·소식 알림을 켜려면 마케팅 수신 동의가 필요합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
