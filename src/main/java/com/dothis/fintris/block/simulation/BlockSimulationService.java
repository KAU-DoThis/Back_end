package com.dothis.fintris.block.simulation;

import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.block.code.TransferErrorCode;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.waitingfund.code.WaitingFundErrorCode;
import com.dothis.fintris.waitingfund.entity.WaitingFund;
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

// 이동·배분 미리보기 계산. 확정(이동·배분)도 같은 계산을 써서 미리보기 값과 확정 값이 같게 한다 (v4 6장)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockSimulationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int VISIBLE_ITEMS = 3;

    private final BlockRepository blockRepository;
    private final BlockTransferRepository blockTransferRepository;
    private final WaitingFundRepository waitingFundRepository;

    // 확정 서비스가 그대로 반영할 수 있게 엔티티와 금액 변화를 같이 돌려준다
    public record Simulation(
            Map<Long, Block> blocks,
            Map<Long, Long> blockDeltas,
            Map<Long, WaitingFund> funds,
            Map<Long, Long> fundDeltas,
            SimulateResponse response
    ) {
    }

    public Simulation simulate(Long userId, List<SimulateRequest.Change> changes) {
        Map<Long, Block> blocks = new LinkedHashMap<>();
        Map<Long, Long> blockDeltas = new LinkedHashMap<>();
        Map<Long, WaitingFund> funds = new LinkedHashMap<>();
        Map<Long, Long> fundDeltas = new LinkedHashMap<>();
        String blockedReason = null;

        for (SimulateRequest.Change change : changes) {
            boolean fromBlock = change.fromBlockId() != null;
            if (fromBlock == (change.fromWaitingFundId() != null)) {
                throw GeneralException.of(TransferErrorCode.TRANSFER_INVALID_SOURCE);
            }
            Block to = blocks.computeIfAbsent(change.toBlockId(), id -> findOwnedBlock(userId, id));
            long amount = change.amount();
            if (fromBlock) {
                if (change.fromBlockId().equals(change.toBlockId())) {
                    blockedReason = "같은 블록으로는 옮길 수 없어요";
                    continue;
                }
                blocks.computeIfAbsent(change.fromBlockId(), id -> findOwnedBlock(userId, id));
                blockDeltas.merge(change.fromBlockId(), -amount, Long::sum);
            } else {
                funds.computeIfAbsent(change.fromWaitingFundId(), id -> findOwnedFund(userId, id));
                fundDeltas.merge(change.fromWaitingFundId(), -amount, Long::sum);
            }
            blockDeltas.merge(to.getId(), amount, Long::sum);
        }

        // 보내는 쪽보다 큰 금액이면 확정할 수 없다 (미리보기는 결과를 보여 주고 blocked로 알린다)
        for (Map.Entry<Long, Long> entry : blockDeltas.entrySet()) {
            if (blocks.get(entry.getKey()).getBalance() + entry.getValue() < 0) {
                blockedReason = "보내는 블록 잔액보다 큰 금액이에요";
            }
        }
        for (Map.Entry<Long, Long> entry : fundDeltas.entrySet()) {
            if (funds.get(entry.getKey()).getAmount() + entry.getValue() < 0) {
                blockedReason = "나눌 돈보다 큰 금액이에요";
            }
        }

        LocalDate today = LocalDate.now(KST);
        List<ImpactItem> items = ImpactCalculator.sorted(impacts(userId, blocks, blockDeltas, today));
        SimulateRequest.Change first = changes.get(0);

        SimulateResponse response = new SimulateResponse(
                blockDeltas.entrySet().stream()
                        .map(entry -> {
                            Block block = blocks.get(entry.getKey());
                            return new SimulateResponse.BlockChange(block.getId(), block.getName(),
                                    block.getBalance(), block.getBalance() + entry.getValue(), block.getVersion());
                        })
                        .toList(),
                items,
                Math.max(0, items.size() - VISIBLE_ITEMS),
                new SimulateResponse.Shortfall(first.toBlockId(), shortfall(blocks.get(first.toBlockId()), today)),
                funds.isEmpty() ? null : funds.values().stream()
                        .mapToLong(fund -> fund.getAmount() + fundDeltas.get(fund.getId()))
                        .sum(),
                items.stream().anyMatch(item -> item.severity() == ImpactItem.Severity.DANGER),
                blockedReason != null,
                blockedReason);
        return new Simulation(blocks, blockDeltas, funds, fundDeltas, response);
    }

    private List<ImpactItem> impacts(Long userId, Map<Long, Block> blocks, Map<Long, Long> deltas, LocalDate today) {
        List<ImpactItem> items = new ArrayList<>();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        for (Map.Entry<Long, Long> entry : deltas.entrySet()) {
            Block block = blocks.get(entry.getKey());
            long before = block.getBalance();
            long after = before + entry.getValue();
            BlockRule rule = block.getRule();
            switch (block.getType()) {
                case SPENDING -> ImpactCalculator.dailyBudget(block.getId(), block.getName(), before, after,
                        block.getDueDate(), today).ifPresent(items::add);
                case GOAL -> {
                    long secured = blockTransferRepository.sumInSince(block.getId(), monthStart)
                            - blockTransferRepository.sumOutSince(block.getId(), monthStart);
                    ImpactCalculator.goal(block.getId(), block.getName(), block.getTargetAmount(),
                            rule != null ? rule.monthlyTarget() : null,
                            before, after, secured, secured + entry.getValue(), today).ifPresent(items::add);
                }
                case FIXED -> payment(block, before, after, today, items);
            }
        }

        // 이번 이동과 상관없는 고정지출도 납부일을 지키는지 같이 보여 준다 (시안 03-2 "월세 납부 영향 없음")
        for (Block block : blockRepository.findAllByUser_IdAndTypeAndDeletedAtIsNullOrderByDisplayOrderAsc(
                userId, BlockType.FIXED)) {
            if (!deltas.containsKey(block.getId())) {
                payment(block, block.getBalance(), block.getBalance(), today, items);
            }
        }
        return items;
    }

    private void payment(Block block, long before, long after, LocalDate today, List<ImpactItem> items) {
        BlockRule rule = block.getRule();
        if (rule != null && rule.payDay() != null && rule.payAmount() != null) {
            items.add(ImpactCalculator.payment(block.getId(), block.getName(), rule.payAmount(), rule.payDay(),
                    before, after, today));
        }
    }

    // [부족한 만큼] 버튼 값: 받는 블록이 이번 기간에 채워야 할 금액 중 모자란 만큼
    private long shortfall(Block block, LocalDate today) {
        BlockRule rule = block.getRule();
        if (rule == null) {
            return 0;
        }
        long need = switch (block.getType()) {
            case FIXED -> rule.payAmount() != null ? rule.payAmount() - block.getBalance() : 0;
            case SPENDING -> rule.monthlyAmount() != null ? rule.monthlyAmount() - block.getBalance() : 0;
            case GOAL -> {
                if (rule.monthlyTarget() == null) {
                    yield 0;
                }
                LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
                long secured = blockTransferRepository.sumInSince(block.getId(), monthStart)
                        - blockTransferRepository.sumOutSince(block.getId(), monthStart);
                yield rule.monthlyTarget() - secured;
            }
        };
        return Math.max(0, need);
    }

    private Block findOwnedBlock(Long userId, Long blockId) {
        Block block = blockRepository.findByIdAndDeletedAtIsNull(blockId)
                .orElseThrow(() -> GeneralException.of(BlockErrorCode.BLOCK_NOT_FOUND));
        if (!block.isOwnedBy(userId)) {
            throw GeneralException.of(BlockErrorCode.BLOCK_FORBIDDEN);
        }
        return block;
    }

    public WaitingFund findOwnedFund(Long userId, Long fundId) {
        WaitingFund fund = waitingFundRepository.findById(fundId)
                .orElseThrow(() -> GeneralException.of(WaitingFundErrorCode.WAITING_FUND_NOT_FOUND));
        if (!fund.isOwnedBy(userId)) {
            throw GeneralException.of(WaitingFundErrorCode.WAITING_FUND_FORBIDDEN);
        }
        return fund;
    }
}
