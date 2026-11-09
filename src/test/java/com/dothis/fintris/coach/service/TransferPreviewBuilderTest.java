package com.dothis.fintris.coach.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.simulation.BlockSimulationService;
import com.dothis.fintris.block.simulation.ImpactItem;
import com.dothis.fintris.block.simulation.SimulateResponse;
import com.dothis.fintris.coach.entity.TransferPreview;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransferPreviewBuilderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private final Block travel = block(3L, BlockType.GOAL, "여행", 320_000, false, null);
    private final Block living = block(1L, BlockType.SPENDING, "생활비", 950_000, true, null);
    private final Block rent = block(2L, BlockType.FIXED, "월세", 600_000, false,
            new BlockRule(null, null, null, 25, 600_000L, null, null));

    @Test
    void 여행에서_생활비로_5만원_옮기는_카드() {
        BlockSimulationService.Simulation simulation = simulation(travel, living, 50_000, List.of(
                new ImpactItem(ImpactItem.Type.GOAL_DATE, ImpactItem.Severity.WARN, 3L,
                        "여행 목표가 약 2주 늦어져요", null, "2026-12-31", "2027-01-14"),
                new ImpactItem(ImpactItem.Type.DAILY_BUDGET, ImpactItem.Severity.GOOD, 1L,
                        "하루 쓸 돈 43,100원 → 45,400원", null, "43100", "45400")));

        TransferPreview preview = TransferPreviewBuilder.build(simulation, 3L, 1L, 50_000, List.of(rent), TODAY);

        assertThat(preview.from()).isEqualTo(new TransferPreview.Side(3L, "여행", BlockType.GOAL, 320_000, 270_000, 4L));
        assertThat(preview.to()).isEqualTo(new TransferPreview.Side(1L, "생활비", BlockType.SPENDING, 950_000, 1_000_000, 4L));
        assertThat(preview.goalDelay()).isEqualTo(new TransferPreview.GoalDelay(3L, "여행", "2026-12-31", "2027-01-14", 14));
        assertThat(preview.dailyBudget()).isEqualTo(new TransferPreview.DailyBudget(43_100, 45_400, 2_300));
        assertThat(preview.fixedImpact().affected()).isFalse();
        assertThat(preview.fixedImpact().items()).containsExactly(
                new TransferPreview.FixedItem(2L, "월세", "2026-11-25", 600_000, 0));
    }

    @Test
    void 고정지출에서_빼면_모자란_금액을_보여준다() {
        BlockSimulationService.Simulation simulation = simulation(rent, living, 60_000, List.of());

        TransferPreview preview = TransferPreviewBuilder.build(simulation, 2L, 1L, 60_000, List.of(rent), TODAY);

        assertThat(preview.fixedImpact().affected()).isTrue();
        assertThat(preview.fixedImpact().items().get(0).shortage()).isEqualTo(60_000);
        // 목표형이 없고 하루 쓸 돈 항목도 없으면 비워 둔다
        assertThat(preview.goalDelay()).isNull();
        assertThat(preview.dailyBudget()).isNull();
    }

    @Test
    void 기본_블록이_아닌_사용형의_하루_쓸_돈은_넣지_않는다() {
        Block extra = block(5L, BlockType.SPENDING, "용돈", 100_000, false, null);
        BlockSimulationService.Simulation simulation = simulation(travel, extra, 50_000, List.of(
                new ImpactItem(ImpactItem.Type.DAILY_BUDGET, ImpactItem.Severity.GOOD, 5L,
                        "하루 쓸 돈 4,500원 → 6,800원", null, "4500", "6800")));

        TransferPreview preview = TransferPreviewBuilder.build(simulation, 3L, 5L, 50_000, List.of(), TODAY);

        assertThat(preview.dailyBudget()).isNull();
        assertThat(preview.fixedImpact().items()).isEmpty();
    }

    private BlockSimulationService.Simulation simulation(Block from, Block to, long amount, List<ImpactItem> items) {
        SimulateResponse response = new SimulateResponse(
                List.of(new SimulateResponse.BlockChange(from.getId(), from.getName(), from.getBalance(),
                                from.getBalance() - amount, from.getVersion()),
                        new SimulateResponse.BlockChange(to.getId(), to.getName(), to.getBalance(),
                                to.getBalance() + amount, to.getVersion())),
                items, 0, new SimulateResponse.Shortfall(to.getId(), 0), null, false, false, null);
        return new BlockSimulationService.Simulation(
                Map.of(from.getId(), from, to.getId(), to),
                Map.of(from.getId(), -amount, to.getId(), amount),
                Map.of(), Map.of(), response);
    }

    private static Block block(Long id, BlockType type, String name, long balance, boolean isDefault, BlockRule rule) {
        Block block = Block.builder()
                .type(type)
                .name(name)
                .balance(balance)
                .isDefault(isDefault)
                .startDate(LocalDate.of(2026, 11, 1))
                .rule(rule)
                .build();
        ReflectionTestUtils.setField(block, "id", id);
        ReflectionTestUtils.setField(block, "version", 4L);
        return block;
    }
}
