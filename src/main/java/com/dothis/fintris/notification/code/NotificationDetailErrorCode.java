package com.dothis.fintris.notification.code;

import com.dothis.fintris.global.api.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NotificationDetailErrorCode implements BaseErrorCode {
    NOTIFICATION_NO_DETAIL(HttpStatus.BAD_REQUEST, "NOTIFICATION_NO_DETAIL", "상세 화면이 없는 알림입니다."),
    NOTIFICATION_BLOCK_GONE(HttpStatus.NOT_FOUND, "NOTIFICATION_BLOCK_GONE", "알림의 블록이 삭제되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
