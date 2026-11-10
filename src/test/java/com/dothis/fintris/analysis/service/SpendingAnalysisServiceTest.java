package com.dothis.fintris.analysis.service;

import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.CategoryItem;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.CompareTo;
import com.dothis.fintris.transaction.repository.CategorySpent;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingAnalysisServiceTest {

    private static final Long USER_ID = 1L;
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Mock TransactionRepository transactionRepository;
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
    @DisplayName("기간 안에 쓴 돈이 없으면 empty")
    void empty() {
        givenSpent(SEPTEMBER, List.of());
        givenSpent(YearMonth.of(2026, 8), List.of());

        assertThat(spendingAnalysisService.getSpending(USER_ID, SEPTEMBER).empty()).isTrue();
    }
}
