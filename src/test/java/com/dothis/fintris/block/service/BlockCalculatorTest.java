package com.dothis.fintris.block.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BlockCalculatorTest {

    @Test
    void 물_높이는_잔액_나누기_capacity를_내림한다() {
        // 시안 01 홈: 생활비 950,000 / 1,500,000 = 63%
        assertThat(BlockCalculator.fillRate(950_000, 1_500_000L)).isEqualTo(63);
        // 목표형 저축 2,000,000 / 3,000,000 = 66.6% → 66
        assertThat(BlockCalculator.fillRate(2_000_000, 3_000_000L)).isEqualTo(66);
    }

    @Test
    void 물_높이는_0에서_100_사이로_자른다() {
        assertThat(BlockCalculator.fillRate(900_000, 600_000L)).isEqualTo(100);
        assertThat(BlockCalculator.fillRate(-10_000, 600_000L)).isZero();
    }

    @Test
    void capacity가_없으면_물_높이도_없다() {
        assertThat(BlockCalculator.fillRate(100_000, null)).isNull();
        assertThat(BlockCalculator.fillRate(100_000, 0L)).isNull();
    }

    @Test
    void 다음_납부일은_오늘을_포함한다() {
        LocalDate today = LocalDate.of(2026, 10, 14);

        assertThat(BlockCalculator.nextPayDate(today, 25)).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(BlockCalculator.nextPayDate(today, 14)).isEqualTo(today);
        assertThat(BlockCalculator.nextPayDate(today, 10)).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void 그_달에_없는_납부일은_말일로_본다() {
        assertThat(BlockCalculator.nextPayDate(LocalDate.of(2026, 2, 1), 31))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(BlockCalculator.nextPayDate(LocalDate.of(2026, 11, 5), 31))
                .isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void 남은_개월은_이번_달과_기한_달을_포함한다() {
        LocalDate today = LocalDate.of(2026, 11, 4);

        assertThat(BlockCalculator.remainingMonths(today, LocalDate.of(2026, 12, 31))).isEqualTo(2);
        assertThat(BlockCalculator.remainingMonths(today, LocalDate.of(2026, 11, 30))).isEqualTo(1);
        assertThat(BlockCalculator.remainingMonths(today, LocalDate.of(2026, 10, 31))).isZero();
    }

    @Test
    void 권장_월_확보_금액은_남은_금액을_남은_개월로_나눠_올림한다() {
        LocalDate today = LocalDate.of(2026, 11, 4);

        // 800,000 - 320,000 = 480,000 ÷ 2개월 = 240,000
        assertThat(BlockCalculator.recommendedMonthlyTarget(800_000, 320_000, today, LocalDate.of(2026, 12, 31)))
                .isEqualTo(240_000);
        // 100,000 ÷ 3개월 = 33,333.3 → 33,334
        assertThat(BlockCalculator.recommendedMonthlyTarget(100_000, 0, today, LocalDate.of(2027, 1, 31)))
                .isEqualTo(33_334);
    }

    @Test
    void 이미_다_모았으면_권장_금액은_0이다() {
        LocalDate today = LocalDate.of(2026, 11, 4);

        assertThat(BlockCalculator.recommendedMonthlyTarget(500_000, 500_000, today, LocalDate.of(2026, 12, 31)))
                .isZero();
    }
}
