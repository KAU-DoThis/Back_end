package com.dothis.fintris.saving.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

// 매일 06시 정기 동기화가 끝난 뒤(06:30) 기간이 끝난 절약 목표를 확정한다
// 사용자마다 따로 처리해서 한 사람이 실패해도 나머지는 확정된다. 실패한 목표는 다음 날 다시 확정 대상이 된다
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "saving.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SavingGoalScheduler {

    private final SavingGoalConfirmService savingGoalConfirmService;

    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Seoul")
    public void confirmEndedGoals() {
        Map<Long, List<Long>> due = savingGoalConfirmService.dueGoalIdsByUser(LocalDate.now(ZoneId.of("Asia/Seoul")));
        int failed = 0;
        for (Map.Entry<Long, List<Long>> entry : due.entrySet()) {
            try {
                savingGoalConfirmService.confirmUser(entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                failed++;
                log.warn("절약 목표 확정 중 예외가 발생했습니다. type={}", e.getClass().getSimpleName());
            }
        }
        log.info("절약 목표 확정을 마쳤습니다. 대상 사용자 수={}, 예외가 난 사용자 수={}", due.size(), failed);
    }
}
