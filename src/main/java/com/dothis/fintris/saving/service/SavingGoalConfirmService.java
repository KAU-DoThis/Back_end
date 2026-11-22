package com.dothis.fintris.saving.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.TransferReason;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.saving.entity.SavingGoal;
import com.dothis.fintris.saving.entity.SavingGoalStatus;
import com.dothis.fintris.saving.repository.SavingGoalRepository;
import com.dothis.fintris.waitingfund.entity.WaitingFund;
import com.dothis.fintris.waitingfund.entity.WaitingFundSource;
import com.dothis.fintris.waitingfund.repository.WaitingFundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 절약 목표 확정 (API 명세 v4 8장 "서버 스케줄러", 기능명세서 F-03·F-05)
// 기간이 끝난 다음 날: 확정 절약액 = 기준 - 실제 → 생활비(기본 블록)에서 꺼내 절약분 나눌 돈으로 옮긴다
// 불변식(블록 합 + 나눌 돈 합 = 실제 잔액)이 깨지지 않게 블록에서 뺀 만큼만 나눌 돈을 만든다
@Service
@RequiredArgsConstructor
public class SavingGoalConfirmService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SavingGoalRepository savingGoalRepository;
    private final SavingGoalService savingGoalService;
    private final BlockRepository blockRepository;
    private final BlockTransferRepository blockTransferRepository;
    private final WaitingFundRepository waitingFundRepository;

    // 한 사용자의 확정 대상 목표를 한 트랜잭션에서 처리한다. 같은 날 끝난 목표는 절약분 하나로 묶는다
    @Transactional
    public void confirmUser(Long userId, List<Long> savingGoalIds) {
        LocalDateTime now = LocalDateTime.now(KST);
        List<SavingGoal> goals = savingGoalRepository.findAllById(savingGoalIds).stream()
                .filter(goal -> goal.getStatus() == SavingGoalStatus.ACTIVE && goal.isOwnedBy(userId))
                .toList();
        if (goals.isEmpty()) {
            return;
        }

        long[] actual = new long[goals.size()];
        long[] saving = new long[goals.size()];
        long total = 0;
        List<WaitingFund.Breakdown> breakdown = new ArrayList<>();
        for (int i = 0; i < goals.size(); i++) {
            SavingGoal goal = goals.get(i);
            actual[i] = savingGoalService.spent(userId, goal.getCategory().getId(), goal.getStartDate(),
                    goal.getEndDate().plusDays(1));
            saving[i] = SavingGoalCalculator.confirmedSaving(goal.getBaseAmount(), actual[i]);
            total += saving[i];
            if (saving[i] > 0) {
                breakdown.add(new WaitingFund.Breakdown(goal.getCategory().getName(), saving[i]));
            }
        }

        // 생활비가 모자라면(절약한 만큼 다른 데 썼으면) 꺼낼 수 있는 만큼만 옮긴다 (F-05 예외 처리)
        Block living = blockRepository.findFirstByUser_IdAndIsDefaultTrueAndDeletedAtIsNull(userId).orElse(null);
        long movable = living == null ? 0 : Math.min(total, Math.max(0, living.getBalance()));

        WaitingFund fund = null;
        if (movable > 0) {
            living.withdraw(movable);
            fund = waitingFundRepository.save(WaitingFund.builder()
                    .user(living.getUser())
                    .source(WaitingFundSource.SAVING)
                    .label(WaitingFundSource.SAVING.getDefaultLabel())
                    .description(goals.get(0).getEndDate().getMonthValue() + "월 절약 확정")
                    .amount(movable)
                    .originalAmount(movable)
                    .savingGoalId(goals.size() == 1 ? goals.get(0).getId() : null)
                    .breakdown(breakdown)
                    .build());
            blockTransferRepository.save(BlockTransfer.builder()
                    .fromBlock(living)
                    .toWaitingFund(fund)
                    .amount(movable)
                    .reason(TransferReason.SAVING)
                    .build());
        }

        Long fundId = fund != null ? fund.getId() : null;
        for (int i = 0; i < goals.size(); i++) {
            goals.get(i).confirm(actual[i], saving[i], saving[i] > 0 ? fundId : null, now);
        }
        // TODO: 확정 알림(SAVING_CONFIRMED)은 알림(F-22)이 붙으면 보낸다 (김지현)
    }

    // 기간이 끝난(종료일 < 오늘) 진행 중 목표를 사용자별로 묶는다
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> dueGoalIdsByUser(LocalDate today) {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (SavingGoal goal : savingGoalRepository.findAllByStatusAndEndDateBefore(SavingGoalStatus.ACTIVE, today)) {
            result.computeIfAbsent(goal.getUser().getId(), key -> new ArrayList<>()).add(goal.getId());
        }
        return result;
    }
}
