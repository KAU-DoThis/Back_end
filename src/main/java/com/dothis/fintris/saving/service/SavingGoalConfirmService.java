package com.dothis.fintris.saving.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.TransferReason;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.saving.entity.SavingGoal;
import com.dothis.fintris.saving.entity.SavingGoalStatus;
import com.dothis.fintris.saving.repository.SavingGoalRepository;
import com.dothis.fintris.transaction.dto.SyncStatus;
import com.dothis.fintris.transaction.service.SyncStateRegistry;
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
    private final LinkedAccountRepository linkedAccountRepository;
    private final SyncStateRegistry syncStateRegistry;

    // 같은 사용자·같은 종료일의 확정 대상. 절약분 나눌 돈 하나로 묶는다
    public record DueGroup(Long userId, LocalDate endDate, List<Long> savingGoalIds) {
    }

    // 기간이 끝난(종료일 < 오늘) 진행 중 목표를 사용자·종료일별로 묶는다
    // 확정이 하루 밀려 종료일이 다른 목표가 함께 대상이 돼도 절약분은 종료일마다 따로 만든다
    @Transactional(readOnly = true)
    public List<DueGroup> dueGroups(LocalDate today) {
        Map<String, DueGroup> groups = new LinkedHashMap<>();
        for (SavingGoal goal : savingGoalRepository.findAllByStatusAndEndDateBefore(SavingGoalStatus.ACTIVE, today)) {
            Long userId = goal.getUser().getId();
            groups.computeIfAbsent(userId + "|" + goal.getEndDate(),
                    key -> new DueGroup(userId, goal.getEndDate(), new ArrayList<>()))
                    .savingGoalIds().add(goal.getId());
        }
        return List.copyOf(groups.values());
    }

    // 기간 중 거래가 다 들어온 뒤에만 확정한다: 동기화가 진행 중이 아니고, 연동한 모든 계좌·카드가
    // 종료일 다음 날 이후에 동기화를 마쳤어야 한다. 아니면 목표는 진행 중으로 남아 다음 날 다시 확정 대상이 된다
    @Transactional(readOnly = true)
    public boolean isSyncedAfter(Long userId, LocalDate endDate) {
        if (syncStateRegistry.get(userId).status() == SyncStatus.IN_PROGRESS) {
            return false;
        }
        LocalDateTime periodOver = endDate.plusDays(1).atStartOfDay();
        return linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId).stream()
                .map(LinkedAccount::getLastSyncedAt)
                .allMatch(syncedAt -> syncedAt != null && !syncedAt.isBefore(periodOver));
    }

    // 한 묶음을 한 트랜잭션에서 처리한다. 목표·블록·나눌 돈은 @Version이 있어서 겹쳐 실행되면
    // 나중 트랜잭션이 통째로 롤백된다 (절약액이 두 번 옮겨지지 않는다)
    @Transactional
    public void confirm(DueGroup group) {
        LocalDateTime now = LocalDateTime.now(KST);
        Long userId = group.userId();
        // 확정 직전에 다시 읽어 진행 중인 내 목표만 남긴다 (그 사이 취소됐으면 건너뛴다)
        List<SavingGoal> goals = savingGoalRepository.findAllById(group.savingGoalIds()).stream()
                .filter(goal -> goal.getStatus() == SavingGoalStatus.ACTIVE && goal.isOwnedBy(userId))
                .toList();
        if (goals.isEmpty()) {
            return;
        }

        SpentStats[] actual = new SpentStats[goals.size()];
        long[] saving = new long[goals.size()];
        long total = 0;
        for (int i = 0; i < goals.size(); i++) {
            SavingGoal goal = goals.get(i);
            actual[i] = savingGoalService.spent(userId, goal.getCategory().getId(), goal.getStartDate(),
                    goal.getEndDate().plusDays(1));
            // 확정은 횟수가 아니라 금액으로 한다. 횟수를 넘겨도 기준보다 적게 썼으면 그만큼 인정한다
            saving[i] = SavingGoalCalculator.confirmedSaving(goal.getBaseAmount(), actual[i].amount());
            total += saving[i];
        }

        // 생활비가 모자라면(절약한 만큼 다른 데 썼으면) 꺼낼 수 있는 만큼만 옮긴다 (F-05 예외 처리)
        Block living = blockRepository.findFirstByUser_IdAndIsDefaultTrueAndDeletedAtIsNull(userId).orElse(null);
        long movable = living == null ? 0 : Math.min(total, Math.max(0, living.getBalance()));

        WaitingFund fund = null;
        if (movable > 0) {
            // 카테고리별 내역 합이 실제로 옮긴 금액과 같도록 같은 비율로 줄인다
            long[] moved = SavingGoalCalculator.scaleDown(saving, total, movable);
            List<WaitingFund.Breakdown> breakdown = new ArrayList<>();
            for (int i = 0; i < goals.size(); i++) {
                if (moved[i] > 0) {
                    breakdown.add(new WaitingFund.Breakdown(goals.get(i).getCategory().getName(), moved[i]));
                }
            }
            living.withdraw(movable);
            fund = waitingFundRepository.save(WaitingFund.builder()
                    .user(living.getUser())
                    .source(WaitingFundSource.SAVING)
                    .label(WaitingFundSource.SAVING.getDefaultLabel())
                    .description(group.endDate().getMonthValue() + "월 절약 확정")
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
            goals.get(i).confirm(actual[i].amount(), Math.toIntExact(actual[i].count()), saving[i],
                    saving[i] > 0 ? fundId : null, now);
        }
        // TODO: 확정 알림(SAVING_CONFIRMED)은 알림(F-22)이 붙으면 보낸다 (김지현)
    }
}
