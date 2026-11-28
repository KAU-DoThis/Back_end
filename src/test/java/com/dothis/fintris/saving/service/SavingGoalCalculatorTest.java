package com.dothis.fintris.saving.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SavingGoalCalculatorTest {

    @Test
    @DisplayName("기준 기간은 시작일이 있는 달의 직전 달 한 달")
    void basePeriod() {
        LocalDate start = LocalDate.of(2026, 11, 25);
        assertThat(SavingGoalCalculator.baseFrom(start)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(SavingGoalCalculator.baseTo(start)).isEqualTo(LocalDate.of(2026, 11, 1));
        // 1월에 시작하면 지난해 12월
        assertThat(SavingGoalCalculator.baseFrom(LocalDate.of(2027, 1, 3))).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    @DisplayName("횟수 목표는 지난달 지출 × 목표 횟수 ÷ 지난달 횟수로 금액을 정한다")
    void targetAmountFromCount() {
        // 택시 지난달 6회 84,000원 → 4회면 56,000원
        assertThat(SavingGoalCalculator.targetAmountFor(84_000, 6, 4)).isEqualTo(56_000);
        assertThat(SavingGoalCalculator.averageAmount(84_000, 6)).isEqualTo(14_000);
        // 1회 평균을 먼저 내림하지 않는다: 100,000원 3회 → 2회면 66,666원 (66,666 = 33,333 × 2가 아님)
        assertThat(SavingGoalCalculator.targetAmountFor(100_000, 3, 2)).isEqualTo(66_666);
        assertThat(SavingGoalCalculator.targetAmountFor(100_000, 3, 0)).isZero();
        // 지난달에 쓴 적이 없으면 평균을 낼 수 없다
        assertThat(SavingGoalCalculator.averageAmount(0, 0)).isNull();
        assertThat(SavingGoalCalculator.targetAmountFor(0, 0, 1)).isZero();
    }

    @Test
    @DisplayName("예상 절약액 = 기준 - 목표, 확정 절약액 = 기준 - 실제 (둘 다 0 미만이면 0)")
    void savings() {
        assertThat(SavingGoalCalculator.expectedSaving(84_000, 56_000)).isEqualTo(28_000);
        assertThat(SavingGoalCalculator.expectedSaving(84_000, 90_000)).isZero();
        // 목표(4회, 56,000원)를 넘겨 5회 60,000원을 썼어도 지난달보다 적게 썼으니 24,000원을 인정한다
        assertThat(SavingGoalCalculator.confirmedSaving(84_000, 60_000)).isEqualTo(24_000);
        assertThat(SavingGoalCalculator.confirmedSaving(84_000, 100_000)).isZero();
    }

    @Test
    @DisplayName("진행률 = 쓴 돈 ÷ 목표 (내림, 100 초과 가능), 목표가 0원이면 null")
    void progressRate() {
        assertThat(SavingGoalCalculator.progressRate(28_000, 56_000)).isEqualTo(50L);
        assertThat(SavingGoalCalculator.progressRate(84_000, 56_000)).isEqualTo(150L);
        assertThat(SavingGoalCalculator.progressRate(1_000, 0)).isNull();
        // int 범위를 넘어도 음수가 되지 않는다
        assertThat(SavingGoalCalculator.progressRate(30_000_000, 1)).isEqualTo(3_000_000_000L);
    }

    @Test
    @DisplayName("옮길 돈이 모자라면 카테고리별 절약액을 같은 비율로 줄이고 합을 정확히 맞춘다")
    void scaleDown() {
        assertThat(SavingGoalCalculator.scaleDown(new long[]{52_000, 35_000}, 87_000, 30_000))
                .containsExactly(17_932, 12_068);
        // 모두 내림하면 0원이고, 남는 2원은 가장 많이 아낀 첫 카테고리에 더한다
        assertThat(SavingGoalCalculator.scaleDown(new long[]{1, 1, 1}, 3, 2)).containsExactly(2, 0, 0);
        // 충분하면 그대로
        assertThat(SavingGoalCalculator.scaleDown(new long[]{52_000, 35_000}, 87_000, 87_000))
                .containsExactly(52_000, 35_000);
    }

    @Test
    @DisplayName("기간을 안 보내면 시작일부터 1개월")
    void defaultEndDate() {
        assertThat(SavingGoalCalculator.defaultEndDate(LocalDate.of(2026, 10, 15))).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(SavingGoalCalculator.defaultEndDate(LocalDate.of(2026, 1, 31))).isEqualTo(LocalDate.of(2026, 2, 27));
    }
}
