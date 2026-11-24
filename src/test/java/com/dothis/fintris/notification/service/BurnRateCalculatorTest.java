package com.dothis.fintris.notification.service;

import com.dothis.fintris.notification.dto.BurnRateDetailResponse.DayAmount;
import com.dothis.fintris.notification.dto.BurnRateDetailResponse.Depletion;
import com.dothis.fintris.notification.dto.BurnRateDetailResponse.TopIncrease;
import com.dothis.fintris.transaction.repository.CategorySpent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BurnRateCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private CategorySpent spent(Long id, String name, long count, long amount) {
        return new CategorySpent(id, null, name, count, amount);
    }

    @Test
    @DisplayName("최근 7일 막대는 오늘까지 빠진 날 없이 채우고, 평소보다 많이 쓴 날만 표시한다")
    void series() {
        List<DayAmount> series = BurnRateCalculator.series(
                Map.of(LocalDate.of(2026, 10, 8), 52_000L, TODAY, 91_000L), TODAY, 58_000L);

        assertThat(series).hasSize(7);
        assertThat(series.getFirst()).isEqualTo(new DayAmount(LocalDate.of(2026, 10, 8), 52_000L, false));
        assertThat(series.get(1)).isEqualTo(new DayAmount(LocalDate.of(2026, 10, 9), 0L, false));
        assertThat(series.getLast()).isEqualTo(new DayAmount(TODAY, 91_000L, true));
    }

    @Test
    @DisplayName("평소는 블록이 최근에 받은 카테고리들의 직전 4주 쓴 돈 ÷ 28이다")
    void usualDailyAvg() {
        List<CategorySpent> recent = List.of(spent(2L, "배달/외식", 4, 120_000), spent(3L, "카페/간식", 6, 36_000));
        List<CategorySpent> usual = List.of(spent(2L, "배달/외식", 10, 280_000), spent(3L, "카페/간식", 8, 56_000),
                spent(5L, "주거/관리비", 1, 500_000));

        assertThat(BurnRateCalculator.usualDailyAvg(recent, usual)).isEqualTo(12_000L); // (280,000 + 56,000) / 28
        assertThat(BurnRateCalculator.usualDailyAvg(recent, List.of())).isNull();
    }

    @Test
    @DisplayName("평소보다 빠르면 비율로, 평소를 모르면 하루 평균으로 말한다")
    void headline() {
        assertThat(BurnRateCalculator.headline(BurnRateCalculator.fasterRate(68_000, 58_000L), 68_000))
                .isEqualTo("평소보다 17% 빨리 줄고 있어요");
        assertThat(BurnRateCalculator.headline(BurnRateCalculator.fasterRate(68_000, null), 68_000))
                .isEqualTo("최근 7일 하루 평균 68,000원을 썼어요");
    }

    @Test
    @DisplayName("이 속도면 바닥나는 날과 기간 끝까지 모자라는 날 수. 기간 안에 버티면 null")
    void depletion() {
        assertThat(BurnRateCalculator.depletion(950_000, 68_000, TODAY, LocalDate.of(2026, 10, 31)))
                .isEqualTo(new Depletion(LocalDate.of(2026, 10, 27), 4));
        assertThat(BurnRateCalculator.depletion(950_000, 10_000, TODAY, LocalDate.of(2026, 10, 31))).isNull();
        assertThat(BurnRateCalculator.depletion(950_000, 0, TODAY, LocalDate.of(2026, 10, 31))).isNull();
    }

    @Test
    @DisplayName("안전하려면 하루 = 잔액 ÷ 남은 일수, 100원 내림")
    void safeDaily() {
        assertThat(BurnRateCalculator.safeDaily(950_000, 18)).isEqualTo(52_700L);
        assertThat(BurnRateCalculator.safeDaily(-1_000, 18)).isZero();
    }

    @Test
    @DisplayName("평소 일주일보다 더 쓴 카테고리만 많이 더 쓴 순으로 3개까지")
    void topIncreases() {
        List<CategorySpent> recent = List.of(spent(2L, "배달/외식", 4, 120_000), spent(3L, "카페/간식", 6, 36_000),
                spent(4L, "교통", 3, 9_000), spent(null, null, 1, 5_000));
        List<CategorySpent> usual = List.of(spent(2L, "배달/외식", 10, 232_000), spent(3L, "카페/간식", 8, 72_000),
                spent(4L, "교통", 12, 60_000));

        List<TopIncrease> increases = BurnRateCalculator.topIncreases(recent, usual);

        assertThat(increases).containsExactly(
                new TopIncrease("배달/외식", "4건", 62_000),
                new TopIncrease("카페/간식", "6건", 18_000),
                new TopIncrease("미분류", "1건", 5_000));
    }
}
