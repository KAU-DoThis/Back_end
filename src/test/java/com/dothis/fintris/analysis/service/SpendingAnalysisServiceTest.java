package com.dothis.fintris.analysis.service;

import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.BiggestIncrease;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.CategoryItem;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.CompareTo;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.TimeOfDay;
import com.dothis.fintris.transaction.repository.CategorySpent;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingAnalysisServiceTest {

    private static final Long USER_ID = 1L;
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Mock TransactionRepository transactionRepository;
    @Mock RecurringSpendingFinder recurringSpendingFinder;
    @InjectMocks SpendingAnalysisService spendingAnalysisService;

    private void givenSpent(YearMonth month, List<CategorySpent> spents) {
        when(transactionRepository.sumSpentByCategory(eq(USER_ID), eq(month.atDay(1).atStartOfDay()),
                eq(month.plusMonths(1).atDay(1).atStartOfDay()))).thenReturn(spents);
    }

    @Test
    @DisplayName("이번 달은 지난달 같은 날짜까지, 지난달에 그 날짜가 없으면 말일까지 비교한다")
    void compareToCurrentMonth() {
        assertThat(SpendingAnalysisService.compareTo(YearMonth.of(2026, 10), 14, true))
                .isEqualTo(new CompareTo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14), "10월 같은 기간"));
        assertThat(SpendingAnalysisService.compareTo(YearMonth.of(2026, 2), 31, true).to())
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("지난 달을 고르면 그 전달 전체와 비교한다")
    void compareToPastMonth() {
        assertThat(SpendingAnalysisService.compareTo(YearMonth.of(2026, 8), 30, false))
                .isEqualTo(new CompareTo(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "8월"));
    }

    @Test
    @DisplayName("증감률은 반올림하고, 지난 기간이 0원이면 null")
    void changeRate() {
        assertThat(SpendingAnalysisService.changeRate(64_000, 524_000)).isEqualTo(12);
        assertThat(SpendingAnalysisService.changeRate(-50_000, 132_000)).isEqualTo(-38);
        assertThat(SpendingAnalysisService.changeRate(10_000, 0)).isNull();
    }

    @Test
    @DisplayName("카테고리는 많이 쓴 순이고, 분류 전 거래는 미분류로 넣어 합계가 맞는다")
    void categories() {
        givenSpent(SEPTEMBER, List.of(
                new CategorySpent(11L, "SUBSCRIPTION", "구독료", 2L, 27_000L),
                new CategorySpent(null, null, null, 1L, 38_000L),
                new CategorySpent(2L, "DELIVERY_DINING", "배달/외식", 13L, 182_000L)));
        givenSpent(YearMonth.of(2026, 8), List.of(
                new CategorySpent(2L, "DELIVERY_DINING", "배달/외식", 9L, 132_000L)));

        SpendingAnalysisResponse response = spendingAnalysisService.getSpending(USER_ID, SEPTEMBER);

        assertThat(response.totalSpent()).isEqualTo(247_000L);
        assertThat(response.previousSpent()).isEqualTo(132_000L);
        assertThat(response.changeAmount()).isEqualTo(115_000L);
        assertThat(response.changeRate()).isEqualTo(87);
        assertThat(response.categories()).containsExactly(
                new CategoryItem(2L, "DELIVERY_DINING", "배달/외식", 13, 182_000L, 50_000L),
                new CategoryItem(null, "UNCLASSIFIED", "미분류", 1, 38_000L, 38_000L),
                new CategoryItem(11L, "SUBSCRIPTION", "구독료", 2, 27_000L, 27_000L));
        assertThat(response.empty()).isFalse();
    }

    @Test
    @DisplayName("지난 기간에 쓴 돈이 없으면 비교 값은 모두 null")
    void noPrevious() {
        givenSpent(SEPTEMBER, List.of(new CategorySpent(2L, "DELIVERY_DINING", "배달/외식", 1L, 10_000L)));
        givenSpent(YearMonth.of(2026, 8), List.of());

        SpendingAnalysisResponse response = spendingAnalysisService.getSpending(USER_ID, SEPTEMBER);

        assertThat(response.previousAvailable()).isFalse();
        assertThat(response.previousSpent()).isNull();
        assertThat(response.changeRate()).isNull();
        assertThat(response.categories().getFirst().changeAmount()).isNull();
    }

    @Test
    @DisplayName("가장 많이 늘어난 카테고리는 미분류를 빼고 고르고, 시간대별 횟수를 센다")
    void biggestIncrease() {
        givenSpent(SEPTEMBER, List.of(
                new CategorySpent(null, null, null, 3L, 300_000L),
                new CategorySpent(2L, "DELIVERY_DINING", "배달/외식", 4L, 182_000L),
                new CategorySpent(3L, "CAFE", "카페/간식", 5L, 30_000L)));
        givenSpent(YearMonth.of(2026, 8), List.of(
                new CategorySpent(2L, "DELIVERY_DINING", "배달/외식", 3L, 132_000L),
                new CategorySpent(3L, "CAFE", "카페/간식", 2L, 10_000L)));
        when(transactionRepository.findSpentTimes(eq(USER_ID), eq(2L), eq(SEPTEMBER.atDay(1).atStartOfDay()),
                eq(SEPTEMBER.plusMonths(1).atDay(1).atStartOfDay()))).thenReturn(List.of(
                LocalDateTime.of(2026, 9, 3, 12, 0), LocalDateTime.of(2026, 9, 5, 22, 30),
                LocalDateTime.of(2026, 9, 9, 23, 0), LocalDateTime.of(2026, 9, 12, 1, 0)));

        BiggestIncrease biggest = spendingAnalysisService.getSpending(USER_ID, SEPTEMBER).biggestIncrease();

        assertThat(biggest.categoryId()).isEqualTo(2L);
        assertThat(biggest.changeAmount()).isEqualTo(50_000L);
        assertThat(biggest.previousAmount()).isEqualTo(132_000L);
        assertThat(biggest.changeRate()).isEqualTo(38);
        assertThat(biggest.timeOfDay()).extracting(TimeOfDay::slot, TimeOfDay::count).containsExactly(
                tuple("MORNING", 0L), tuple("LUNCH", 1L), tuple("EVENING", 0L), tuple("NIGHT", 3L));
        assertThat(biggest.timeSummary()).isEqualTo("4번 중 3번이 밤 10시 이후였어요");
        assertThat(biggest.explanation()).isEqualTo("횟수가 3번에서 4번으로 늘었고, 한 번에 쓰는 돈은 비슷해요.");
    }

    @Test
    @DisplayName("설명 문장은 횟수와 건당 금액 중 무엇 때문에 늘었는지 말한다")
    void explanation() {
        assertThat(SpendingAnalysisService.explanation(0, 4, 0, 40_000))
                .isEqualTo("지난 기간엔 없던 지출이 4번 생겼어요.");
        // 건당 14,667원 → 14,000원 (±10% 안)
        assertThat(SpendingAnalysisService.explanation(9, 13, 132_000, 182_000))
                .isEqualTo("횟수가 9번에서 13번으로 늘었고, 한 번에 쓰는 돈은 비슷해요.");
        assertThat(SpendingAnalysisService.explanation(2, 3, 20_000, 60_000))
                .isEqualTo("횟수가 2번에서 3번으로 늘었고, 한 번에 쓰는 돈은 더 커졌어요.");
        assertThat(SpendingAnalysisService.explanation(2, 4, 40_000, 50_000))
                .isEqualTo("횟수가 2번에서 4번으로 늘었고, 한 번에 쓰는 돈은 더 작아졌어요.");
        assertThat(SpendingAnalysisService.explanation(5, 5, 50_000, 80_000))
                .isEqualTo("횟수는 5번으로 같지만, 한 번에 쓰는 돈이 커졌어요.");
        assertThat(SpendingAnalysisService.explanation(6, 4, 60_000, 80_000))
                .isEqualTo("횟수는 6번에서 4번으로 줄었지만, 한 번에 쓰는 돈이 커졌어요.");
    }

    @Test
    @DisplayName("늘어난 카테고리가 없으면 biggestIncrease는 null")
    void noIncrease() {
        givenSpent(SEPTEMBER, List.of(new CategorySpent(2L, "DELIVERY_DINING", "배달/외식", 1L, 10_000L)));
        givenSpent(YearMonth.of(2026, 8), List.of(new CategorySpent(2L, "DELIVERY_DINING", "배달/외식", 2L, 20_000L)));

        assertThat(spendingAnalysisService.getSpending(USER_ID, SEPTEMBER).biggestIncrease()).isNull();
    }

    @Test
    @DisplayName("한 시간대에 절반 이상 몰리지 않으면 시간대 요약은 null")
    void timeSummarySpread() {
        assertThat(SpendingAnalysisService.timeSummary(5, Map.of(TimeSlot.LUNCH, 2L, TimeSlot.NIGHT, 2L, TimeSlot.MORNING, 1L)))
                .isNull();
        // 같은 횟수면 이른 시간대를 말한다 (서비스는 EnumMap으로 넘긴다)
        assertThat(SpendingAnalysisService.timeSummary(4, new EnumMap<>(Map.of(TimeSlot.LUNCH, 2L, TimeSlot.NIGHT, 2L))))
                .isEqualTo("4번 중 2번이 점심 11~15시였어요");
    }

    @Test
    @DisplayName("시간대 경계: 6·11·15·22시는 다음 구간, 새벽은 밤")
    void timeSlotBoundaries() {
        assertThat(TimeSlot.of(LocalTime.of(5, 59))).isEqualTo(TimeSlot.NIGHT);
        assertThat(TimeSlot.of(LocalTime.of(6, 0))).isEqualTo(TimeSlot.MORNING);
        assertThat(TimeSlot.of(LocalTime.of(11, 0))).isEqualTo(TimeSlot.LUNCH);
        assertThat(TimeSlot.of(LocalTime.of(15, 0))).isEqualTo(TimeSlot.EVENING);
        assertThat(TimeSlot.of(LocalTime.of(22, 0))).isEqualTo(TimeSlot.NIGHT);
    }

    @Test
    @DisplayName("기간 안에 쓴 돈이 없으면 empty")
    void empty() {
        givenSpent(SEPTEMBER, List.of());
        givenSpent(YearMonth.of(2026, 8), List.of());

        assertThat(spendingAnalysisService.getSpending(USER_ID, SEPTEMBER).empty()).isTrue();
    }
}
