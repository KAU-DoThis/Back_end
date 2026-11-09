package com.dothis.fintris.coach.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.service.BlockCalculator;
import com.dothis.fintris.block.simulation.BlockSimulationService;
import com.dothis.fintris.block.simulation.ImpactItem;
import com.dothis.fintris.block.simulation.SimulateResponse;
import com.dothis.fintris.coach.entity.TransferPreview;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

// 이동 미리보기(/blocks/simulate) 결과를 제안 카드 항목별 숫자로 나눈다. 계산은 미리보기 것을 그대로 쓴다
public final class TransferPreviewBuilder {

    private TransferPreviewBuilder() {
    }

    // fixedBlocks: 사용자의 고정지출형 블록 전부 (이번 이동과 상관없는 것도 납부 영향을 보여 준다)
    public static TransferPreview build(BlockSimulationService.Simulation simulation, Long fromBlockId,
                                        Long toBlockId, long amount, List<Block> fixedBlocks, LocalDate today) {
        SimulateResponse response = simulation.response();
        Map<Long, Block> blocks = simulation.blocks();

        return new TransferPreview(
                amount,
                side(response, blocks.get(fromBlockId)),
                side(response, blocks.get(toBlockId)),
                goalDelay(response.items(), blocks),
                dailyBudget(response.items(), blocks),
                fixedImpact(fixedBlocks, simulation.blockDeltas(), today),
                response.requiresConfirm(),
                response.blocked(),
                response.blockedReason());
    }

    private static TransferPreview.Side side(SimulateResponse response, Block block) {
        long after = response.blocks().stream()
                .filter(change -> change.blockId().equals(block.getId()))
                .mapToLong(SimulateResponse.BlockChange::balanceAfter)
                .findFirst()
                .orElse(block.getBalance());
        return new TransferPreview.Side(block.getId(), block.getName(), block.getType(),
                block.getBalance(), after, block.getVersion());
    }

    // 목표형 예상 달성일이 바뀐 블록. 보내는 쪽과 받는 쪽이 둘 다 목표형이면 늦어지는 쪽(먼저 정렬된 warn)을 보여 준다
    private static TransferPreview.GoalDelay goalDelay(List<ImpactItem> items, Map<Long, Block> blocks) {
        return items.stream()
                .filter(item -> item.type() == ImpactItem.Type.GOAL_DATE)
                .findFirst()
                .map(item -> new TransferPreview.GoalDelay(item.blockId(), blocks.get(item.blockId()).getName(),
                        item.before(), item.after(),
                        ChronoUnit.DAYS.between(LocalDate.parse(item.before()), LocalDate.parse(item.after()))))
                .orElse(null);
    }

    // 하루 쓸 돈은 홈과 같은 기본 사용형 블록 기준. 기본 블록이 안 움직이거나 값이 그대로면 null
    private static TransferPreview.DailyBudget dailyBudget(List<ImpactItem> items, Map<Long, Block> blocks) {
        return items.stream()
                .filter(item -> item.type() == ImpactItem.Type.DAILY_BUDGET)
                .filter(item -> blocks.get(item.blockId()).isDefault())
                .findFirst()
                .map(item -> {
                    long before = Long.parseLong(item.before());
                    long after = Long.parseLong(item.after());
                    return new TransferPreview.DailyBudget(before, after, after - before);
                })
                .orElse(null);
    }

    private static TransferPreview.FixedImpact fixedImpact(List<Block> fixedBlocks, Map<Long, Long> deltas,
                                                           LocalDate today) {
        List<TransferPreview.FixedItem> items = fixedBlocks.stream()
                .filter(block -> block.getRule() != null && block.getRule().payDay() != null
                        && block.getRule().payAmount() != null)
                .map(block -> {
                    BlockRule rule = block.getRule();
                    long after = block.getBalance() + deltas.getOrDefault(block.getId(), 0L);
                    return new TransferPreview.FixedItem(block.getId(), block.getName(),
                            BlockCalculator.nextPayDate(today, rule.payDay()).toString(),
                            rule.payAmount(), Math.max(0, rule.payAmount() - after));
                })
                .toList();
        return new TransferPreview.FixedImpact(items.stream().anyMatch(item -> item.shortage() > 0), items);
    }
}
