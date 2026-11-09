package com.dothis.fintris.block.service;

import com.dothis.fintris.block.dto.BlockDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BurnCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 30);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private Map<LocalDate, Long> everyDay(long amount, int days) {
        Map<LocalDate, Long> spent = new HashMap<>();
        for (int i = 0; i < days; i++) {
            spent.put(START.plusDays(i), amount);
        }
        return spent;
    }

    @Test
    @DisplayName("계획보다 빨리 쓰면 양수 비율·'빠름'이고, 지금 속도로 잔액이 바닥나는 날을 알려준다")
    void faster() {
        BlockDetailResponse.Burn burn = BurnCalculator.calculate(1_500_000L, START, END, TODAY, 900_000L, everyDay(60_000, 10));

        assertThat(burn.actualSpent()).isEqualTo(600_000L);
        assertThat(burn.series()).hasSize(10);
        assertThat(burn.series().getLast()).isEqualTo(new BlockDetailResponse.BurnPoint(TODAY, 500_000L, 600_000L));
        assertThat(burn.paceDiffRate()).isEqualTo(20);
        assertThat(burn.paceLabel()).isEqualTo("계획보다 20% 빠름");
        assertThat(burn.expectedDepletionDate()).isEqualTo(LocalDate.of(2026, 11, 25));
    }

    @Test
    @DisplayName("계획보다 천천히 쓰면 음수 비율·'느림'이고, 기간 안에 안 바닥나면 예상 소진일은 null")
    void slower() {
        BlockDetailResponse.Burn burn = BurnCalculator.calculate(1_500_000L, START, END, TODAY, 1_200_000L, everyDay(30_000, 10));

        assertThat(burn.paceDiffRate()).isEqualTo(-40);
        assertThat(burn.paceLabel()).isEqualTo("계획보다 40% 느림");
        assertThat(burn.expectedDepletionDate()).isNull();
    }

    @Test
    @DisplayName("쓴 돈이 없으면 예상 소진일이 없고, 기간이 지났으면 그래프는 기간 끝까지만 그린다")
    void edges() {
        assertThat(BurnCalculator.calculate(1_500_000L, START, END, TODAY, 1_500_000L, Map.of()).expectedDepletionDate())
                .isNull();
        assertThat(BurnCalculator.calculate(1_500_000L, START, END, LocalDate.of(2026, 12, 5), 0L, Map.of()).series())
                .hasSize(30);
        assertThat(BurnCalculator.paceLabel(0)).isEqualTo("계획대로");
    }

    @Test
    @DisplayName("월 배분액이 없거나 기간 시작 전이면 그래프가 없다")
    void noBurn() {
        assertThat(BurnCalculator.calculate(null, START, END, TODAY, 0L, Map.of())).isNull();
        assertThat(BurnCalculator.calculate(1_500_000L, START, END, START.minusDays(1), 0L, Map.of())).isNull();
    }
}
