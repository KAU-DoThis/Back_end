package com.dothis.fintris.saving.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

// 매일 06시 정기 동기화가 끝난 뒤(06:30) 기간이 끝난 절약 목표를 확정한다
// 사용자·종료일 묶음마다 따로 처리해서 하나가 실패해도 나머지는 확정된다
// 동기화가 덜 끝났거나 실패했거나 충돌로 롤백된 묶음은 진행 중으로 남아 다음 날 다시 확정 대상이 된다
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "saving.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SavingGoalScheduler {

    private final SavingGoalConfirmService savingGoalConfirmService;

    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Seoul")
    public void confirmEndedGoals() {
        List<SavingGoalConfirmService.DueGroup> groups =
                savingGoalConfirmService.dueGroups(LocalDate.now(ZoneId.of("Asia/Seoul")));
        int waiting = 0;
        int failed = 0;
        for (SavingGoalConfirmService.DueGroup group : groups) {
            if (!savingGoalConfirmService.isSyncedAfter(group.userId(), group.endDate())) {
                waiting++;
                continue;
            }
            try {
                savingGoalConfirmService.confirm(group);
            } catch (RuntimeException e) {
                failed++;
                log.warn("절약 목표 확정 중 예외가 발생했습니다. type={}", e.getClass().getSimpleName());
            }
        }
        log.info("절약 목표 확정을 마쳤습니다. 묶음 수={}, 동기화 대기={}, 예외={}", groups.size(), waiting, failed);
    }
}
