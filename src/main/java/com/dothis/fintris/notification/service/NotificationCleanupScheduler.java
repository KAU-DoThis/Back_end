package com.dothis.fintris.notification.service;

import com.dothis.fintris.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 30일이 지난 알림은 매일 새벽 4시에 지운다 (v4 9장)
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationRepository notificationRepository;

    @Transactional
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void deleteExpired() {
        int deleted = notificationRepository.deleteCreatedBefore(
                LocalDateTime.now().minusDays(NotificationService.RETENTION_DAYS));
        log.info("기간이 지난 알림을 지웠습니다. 건수={}", deleted);
    }
}
