package com.dothis.fintris.notification.push;

import com.dothis.fintris.notification.service.NotificationService.NotificationCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// 알림 저장이 확정된 뒤에만 푸시를 보낸다. 알림을 만든 쪽이 롤백되면 보내지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class PushEventListener {

    private final PushDispatcher pushDispatcher;

    @TransactionalEventListener
    public void onCreated(NotificationCreated event) {
        try {
            pushDispatcher.dispatch(event.notificationId());
        } catch (RuntimeException e) {
            log.warn("푸시 발송 중 예외가 발생했습니다. notificationId={}", event.notificationId());
        }
    }
}
