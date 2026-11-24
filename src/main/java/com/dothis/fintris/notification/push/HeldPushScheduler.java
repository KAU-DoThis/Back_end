package com.dothis.fintris.notification.push;

import com.dothis.fintris.notification.entity.Notification;
import com.dothis.fintris.notification.entity.PushStatus;
import com.dothis.fintris.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// 받지 않는 시간·모아 보내기로 미룬 푸시를 10분마다 다시 본다. 시간이 지났으면 보내고, 아니면 계속 미룬다
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class HeldPushScheduler {

    private static final int MAX_HELD_HOURS = 24;

    private final NotificationRepository notificationRepository;
    private final PushDispatcher pushDispatcher;

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void sendHeld() {
        for (Notification notification : notificationRepository.findAllByPushStatusAndCreatedAtGreaterThanEqual(
                PushStatus.HELD, LocalDateTime.now().minusHours(MAX_HELD_HOURS))) {
            try {
                pushDispatcher.dispatch(notification.getId());
            } catch (RuntimeException e) {
                log.warn("미룬 푸시 발송 중 예외가 발생했습니다. notificationId={}", notification.getId());
            }
        }
    }
}
