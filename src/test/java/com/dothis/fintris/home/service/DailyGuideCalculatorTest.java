package com.dothis.fintris.home.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DailyGuideCalculatorTest {

    @Test
    void 남은_일수는_오늘을_포함한다() {
        LocalDate today = LocalDate.of(2026, 10, 14);

        assertThat(DailyGuideCalculator.remainingDays(today, LocalDate.of(2026, 10, 31))).isEqualTo(18);
        assertThat(DailyGuideCalculator.remainingDays(today, today)).isEqualTo(1);
    }

    @Test
    void 기간이_끝났으면_남은_일수는_0이다() {
        LocalDate today = LocalDate.of(2026, 11, 1);

        assertThat(DailyGuideCalculator.remainingDays(today, LocalDate.of(2026, 10, 31))).isZero();
    }

    @Test
    void 기준_금액은_100원_단위로_내린다() {
        // 디자인 홈 화면: 950,000 ÷ 18 = 52,777 → 52,700
        assertThat(DailyGuideCalculator.todayAvailable(950_000, 18)).isEqualTo(52_700);
        assertThat(DailyGuideCalculator.todayAvailable(1_000, 3)).isEqualTo(300);
    }

    @Test
    void 잔액이_0_이하면_0원이다() {
        assertThat(DailyGuideCalculator.todayAvailable(0, 10)).isZero();
        assertThat(DailyGuideCalculator.todayAvailable(-5_000, 10)).isZero();
    }

    @Test
    void 남은_일수가_없으면_0원이다() {
        assertThat(DailyGuideCalculator.todayAvailable(950_000, 0)).isZero();
    }
}
