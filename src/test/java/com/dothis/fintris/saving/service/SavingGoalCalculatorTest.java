package com.dothis.fintris.saving.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SavingGoalCalculatorTest {

    @Test
    @DisplayName("기준 지출액은 시작일이 있는 달의 직전 3개월 합 ÷ 3 (내림)")
    void baseAmount() {
        LocalDate start = LocalDate.of(2026, 11, 22);
        assertThat(SavingGoalCalculator.baseFrom(start)).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(SavingGoalCalculator.baseTo(start)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(SavingGoalCalculator.baseAmount(252_000)).isEqualTo(84_000);
        assertThat(SavingGoalCalculator.baseAmount(100_000)).isEqualTo(33_333);
    }

    @Test
    @DisplayName("예상 절약액 = 기준 - 목표, 확정 절약액 = 기준 - 실제 (둘 다 0 미만이면 0)")
    void savings() {
        assertThat(SavingGoalCalculator.expectedSaving(84_000, 42_000)).isEqualTo(42_000);
        assertThat(SavingGoalCalculator.expectedSaving(84_000, 90_000)).isZero();
        // 목표(42,000)를 넘겨 60,000원을 썼어도 기준보다 적게 썼으니 24,000원을 인정한다
        assertThat(SavingGoalCalculator.confirmedSaving(84_000, 60_000)).isEqualTo(24_000);
        assertThat(SavingGoalCalculator.confirmedSaving(84_000, 100_000)).isZero();
    }

    @Test
    @DisplayName("진행률 = 쓴 돈 ÷ 목표 (내림, 100 초과 가능), 목표가 0원이면 null")
    void progressRate() {
        assertThat(SavingGoalCalculator.progressRate(21_000, 42_000)).isEqualTo(50);
        assertThat(SavingGoalCalculator.progressRate(63_000, 42_000)).isEqualTo(150);
        assertThat(SavingGoalCalculator.progressRate(1_000, 0)).isNull();
    }

    @Test
    @DisplayName("기간을 안 보내면 시작일부터 1개월")
    void defaultEndDate() {
        assertThat(SavingGoalCalculator.defaultEndDate(LocalDate.of(2026, 10, 15))).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(SavingGoalCalculator.defaultEndDate(LocalDate.of(2026, 1, 31))).isEqualTo(LocalDate.of(2026, 2, 27));
    }
}
