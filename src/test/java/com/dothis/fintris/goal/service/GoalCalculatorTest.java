package com.dothis.fintris.goal.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class GoalCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    @Test
    @DisplayName("달성률은 잔액 ÷ 목표 금액을 내림하고 0~100으로 자른다")
    void achievementRate() {
        assertThat(GoalCalculator.achievementRate(320_000, 800_000)).isEqualTo(40);
        assertThat(GoalCalculator.achievementRate(333_333, 1_000_000)).isEqualTo(33);
        assertThat(GoalCalculator.achievementRate(900_000, 800_000)).isEqualTo(100);
        assertThat(GoalCalculator.achievementRate(100, 0)).isZero();
    }

    @Test
    @DisplayName("평균을 낼 달은 만든 달을 빼고 지난달까지, 최대 3개월")
    void paceMonths() {
        assertThat(GoalCalculator.paceMonths(LocalDate.of(2026, 11, 3), TODAY)).isZero();  // 이번 달에 만듦
        assertThat(GoalCalculator.paceMonths(LocalDate.of(2026, 10, 15), TODAY)).isZero(); // 지난달에 만듦
        assertThat(GoalCalculator.paceMonths(LocalDate.of(2026, 9, 1), TODAY)).isEqualTo(1);  // 10월
        assertThat(GoalCalculator.paceMonths(LocalDate.of(2026, 3, 1), TODAY)).isEqualTo(3);  // 8~10월
    }

    @Test
    @DisplayName("지난 달 평균 확보액이 있으면 그 값, 없으면 월 확보 금액으로 본다")
    void paceMonthly() {
        assertThat(GoalCalculator.paceMonthly(450_000, 3, 200_000L)).isEqualTo(150_000);
        assertThat(GoalCalculator.paceMonthly(0, 0, 200_000L)).isEqualTo(200_000);
        // 지난 달에 오히려 빼 썼으면 평균을 쓰지 않는다
        assertThat(GoalCalculator.paceMonthly(-50_000, 2, 200_000L)).isEqualTo(200_000);
        assertThat(GoalCalculator.paceMonthly(0, 0, null)).isNull();
    }

    @Test
    @DisplayName("예상 달성일과 늦어지는 일수를 손 계산과 같게 낸다")
    void paceDateAndDelay() {
        // 남은 480,000원을 월 200,000원씩 → 72일 뒤 (하루치 = 월 ÷ 30)
        LocalDate pace = GoalCalculator.paceDate(800_000, 320_000, 200_000L, TODAY);
        assertThat(pace).isEqualTo(TODAY.plusDays(72));
        assertThat(GoalCalculator.delayDays(LocalDate.of(2026, 12, 31), pace)).isEqualTo(33);
        // 목표일보다 빠르면 0, 계산할 수 없으면 null
        assertThat(GoalCalculator.delayDays(LocalDate.of(2027, 6, 30), pace)).isZero();
        assertThat(GoalCalculator.delayDays(LocalDate.of(2026, 12, 31), null)).isNull();
        // 다 모았으면 오늘
        assertThat(GoalCalculator.paceDate(800_000, 800_000, null, TODAY)).isEqualTo(TODAY);
    }
}
