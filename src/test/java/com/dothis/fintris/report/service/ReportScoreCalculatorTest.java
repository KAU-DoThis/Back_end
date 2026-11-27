package com.dothis.fintris.report.service;

import com.dothis.fintris.report.service.ReportScoreCalculator.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportScoreCalculatorTest {

    private static List<Item> items(Integer fixed, Integer pace, Integer saving, Integer goal) {
        return List.of(new Item("FIXED", "고정지출 확보", 30, fixed), new Item("PACE", "생활비 페이스", 30, pace),
                new Item("SAVING", "절약 실천", 15, saving), new Item("GOAL", "목표 달성률", 25, goal));
    }

    @Test
    @DisplayName("점수 = Σ(달성률 × 배점) ÷ 100, 반올림 (시안: 100·58·89·67 → 78점)")
    void total() {
        // 3,000 + 1,740 + 1,335 + 1,675 = 7,750 → 77.5 → 78
        assertThat(ReportScoreCalculator.total(items(100, 58, 89, 67))).isEqualTo(78);
        assertThat(ReportScoreCalculator.total(items(100, 100, 100, 100))).isEqualTo(100);
        assertThat(ReportScoreCalculator.total(items(0, 0, 0, 0))).isZero();
    }

    @Test
    @DisplayName("해당 없는 항목의 배점은 나머지에 비율대로 나눈다")
    void redistribute() {
        // 절약 목표 없음: (100×30 + 50×30 + 80×25) ÷ 85 = 6,500 ÷ 85 = 76.47 → 76
        assertThat(ReportScoreCalculator.total(items(100, 50, null, 80))).isEqualTo(76);
        assertThat(ReportScoreCalculator.total(items(null, null, null, 40))).isEqualTo(40);
        assertThat(ReportScoreCalculator.total(items(null, null, null, null))).isNull();
    }

    @Test
    @DisplayName("비율은 반올림하고 0~100으로 자른다, 분모가 0이면 null")
    void rate() {
        assertThat(ReportScoreCalculator.rate(87_000, 98_000)).isEqualTo(89); // 88.78
        assertThat(ReportScoreCalculator.rate(150_000, 100_000)).isEqualTo(100);
        assertThat(ReportScoreCalculator.rate(-10_000, 100_000)).isZero();
        assertThat(ReportScoreCalculator.rate(1, 0)).isNull();
    }

    @Test
    @DisplayName("정수 반올림: 양수·음수 모두 0.5에서 멀어지는 쪽으로")
    void roundDiv() {
        assertThat(ReportScoreCalculator.roundDiv(-9_000_000, 2_230_000)).isEqualTo(-4); // -4.03
        assertThat(ReportScoreCalculator.roundDiv(155, 10)).isEqualTo(16);
        assertThat(ReportScoreCalculator.roundDiv(-155, 10)).isEqualTo(-16);
    }

    @Test
    @DisplayName("생활비 페이스: 날마다 누적 소진율이 경과율 + 10%p 이내인 날의 비율")
    void paceRate() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        // 월 300,000원. 1일 30,000원(소진 10%, 경과 3.3% → +6.7%p 안전), 2일 60,000원 더(30% vs 6.7% → +23.3%p 주의)
        long[] daily = {30_000, 60_000, 0, 0};
        // 3일 30% vs 10% → +20%p 주의, 4일 30% vs 13.3% → +16.7%p 주의 → 안전 1일 / 4일
        assertThat(ReportScoreCalculator.paceRate(daily, 300_000, start, end, start)).isEqualTo(25);
        // 매일 하루치(10,000원)씩 쓰면 모두 안전
        long[] even = new long[30];
        java.util.Arrays.fill(even, 10_000);
        assertThat(ReportScoreCalculator.paceRate(even, 300_000, start, end, start)).isEqualTo(100);
        assertThat(ReportScoreCalculator.paceRate(even, 0, start, end, start)).isNull();
    }
}
