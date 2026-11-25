package com.dothis.fintris.block.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.block.service.BlockStatusJudge.Judgement;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.waitingfund.repository.WaitingFundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// 블록 상태 판정에 필요한 숫자(쓴 돈, 나눌 돈, 이번 달 확보액, 납부 여부)를 모아 BlockStatusJudge에 넘긴다 (#58)
// 블록 목록·상세·홈·이동 결과가 모두 이 판정을 써서 같은 블록은 어디서나 같은 상태로 보인다
@Service
@RequiredArgsConstructor
public class BlockStatusService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TransactionRepository transactionRepository;
    private final WaitingFundRepository waitingFundRepository;
    private final BlockTransferRepository blockTransferRepository;

    public Judgement judge(Block block) {
        return judgeAll(java.util.List.of(block)).get(block.getId());
    }

    // 나눌 돈 합계는 사용자마다 한 번만 센다
    public Map<Long, Judgement> judgeAll(Collection<Block> blocks) {
        LocalDate today = LocalDate.now(KST);
        Map<Long, Long> waitingFundByUser = new HashMap<>();
        Map<Long, Judgement> result = new LinkedHashMap<>();
        for (Block block : blocks) {
            result.put(block.getId(), judge(block, today, waitingFundByUser));
        }
        return result;
    }

    private Judgement judge(Block block, LocalDate today, Map<Long, Long> waitingFundByUser) {
        if (block.getDeletedAt() != null || block.getType() == null) {
            return Judgement.unknown();
        }
        BlockRule rule = block.getRule();
        return switch (block.getType()) {
            case SPENDING -> {
                // 기간이 끝난 뒤 반영된 거래는 이번 기간 소진율에 넣지 않는다
                LocalDate start = block.getStartDate();
                LocalDate end = block.getDueDate();
                LocalDate until = end == null || end.isAfter(today) ? today : end;
                long spent = start == null || block.getId() == null ? 0
                        : transactionRepository.sumBlockSpent(block.getId(), start.atStartOfDay(),
                        until.plusDays(1).atStartOfDay()).spent();
                yield BlockStatusJudge.spending(block.getBalance(), rule != null ? rule.monthlyAmount() : null,
                        start, block.getDueDate(), today, spent);
            }
            case FIXED -> {
                Long userId = block.getUser().getId();
                long waitingFunds = waitingFundByUser.computeIfAbsent(userId, waitingFundRepository::sumAmountByUserId);
                // 금액 범위 조건이면 일부만 낸 거래도 연결될 수 있어서, 이번 달에 낸 금액 합을 넘겨 부족액에서 뺀다
                Long payAmount = rule != null ? rule.payAmount() : null;
                long paidThisMonth = block.getId() == null || payAmount == null || payAmount <= 0 ? 0
                        : transactionRepository.sumBlockPaidSince(block.getId(), monthStart(today));
                yield BlockStatusJudge.fixed(block.getBalance(), payAmount,
                        rule != null ? rule.payDay() : null, today, waitingFunds, paidThisMonth);
            }
            case GOAL -> {
                long target = block.getTargetAmount() != null ? block.getTargetAmount() : 0;
                long secured = block.getId() == null ? 0
                        : blockTransferRepository.sumInSince(block.getId(), monthStart(today))
                        - blockTransferRepository.sumOutSince(block.getId(), monthStart(today));
                yield BlockStatusJudge.goal(block.getBalance(), target, monthlyTarget(block, target, today),
                        block.getDueDate(), today, secured);
            }
        };
    }

    // 월 확보 금액을 정하지 않았으면 기한까지 남은 달로 나눈 권장값으로 본다
    private Long monthlyTarget(Block block, long target, LocalDate today) {
        BlockRule rule = block.getRule();
        if (rule != null && rule.monthlyTarget() != null) {
            return rule.monthlyTarget();
        }
        if (block.getDueDate() == null) {
            return null;
        }
        return BlockCalculator.recommendedMonthlyTarget(target, block.getBalance(), today, block.getDueDate());
    }

    private static LocalDateTime monthStart(LocalDate today) {
        return today.withDayOfMonth(1).atStartOfDay();
    }
}
