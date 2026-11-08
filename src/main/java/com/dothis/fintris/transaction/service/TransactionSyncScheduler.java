package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.repository.LinkedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 하루 3번(06·13·20시) 연동된 모든 사용자의 거래내역을 동기화한다. 서버 한 대 기준이라 중복 실행은 막지 않는다
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sync.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionSyncScheduler {

    private final LinkedAccountRepository linkedAccountRepository;
    private final ManualSyncService manualSyncService;

    @Scheduled(cron = "0 0 6,13,20 * * *", zone = "Asia/Seoul")
    public void syncAllUsers() {
        int failed = 0;
        for (Long userId : linkedAccountRepository.findUserIdsWithActive()) {
            try {
                manualSyncService.runScheduled(userId);
            } catch (RuntimeException e) {
                failed++;
                log.warn("정기 동기화 중 예외가 발생했습니다.");
            }
        }
        log.info("정기 동기화를 마쳤습니다. 예외가 난 사용자 수={}", failed);
    }
}
