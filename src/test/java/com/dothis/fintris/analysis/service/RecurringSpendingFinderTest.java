package com.dothis.fintris.analysis.service;

import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.Recurring;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.service.RecurringPaymentDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringSpendingFinderTest {

    private static final LocalDate PERIOD_FROM = LocalDate.of(2026, 10, 1);
    private static final Category CAFE = category(3L, "CAFE", "카페/간식");
    private static final Category SUBSCRIPTION = category(11L, "SUBSCRIPTION", "구독료");
    private static final Category TELECOM = category(6L, "TELECOM", "통신");
    private static final Category TRANSPORT = category(4L, "TRANSPORT", "교통");

    private final RecurringSpendingFinder finder = new RecurringSpendingFinder(new RecurringPaymentDetector());

    private static Category category(Long id, String code, String name) {
        return Category.builder().id(id).code(code).name(name).categoryType(CategoryType.EXPENSE).build();
    }

    private Transaction spent(Category category, String merchant, int month, int day, long amount) {
        return Transaction.builder().occurredAt(LocalDateTime.of(2026, month, day, 9, 0)).amount(amount)
                .direction(TransactionDirection.OUT).merchantOrPayee(merchant).category(category)
                .source(TransactionSource.CODEF).status(TransactionStatus.CONFIRMED).build();
    }

    @Test
    @DisplayName("전체 결제의 20% 이상, 4번 이상 쓴 카테고리는 반복 소비이고 가장 많이 간 곳을 보여준다")
    void repeat() {
        List<Transaction> history = new ArrayList<>();
        for (int day = 1; day <= 4; day++) {
            history.add(spent(CAFE, "스타벅스", 10, day, 5_000));
        }
        history.add(spent(CAFE, "이디야", 10, 10, 3_000));

        assertThat(finder.find(history, PERIOD_FROM))
                .containsExactly(new Recurring("REPEAT", "카페/간식", "5회 · 스타벅스 4회, 그 외 1회", 23_000));
    }

    @Test
    @DisplayName("가맹점 이름이 없는 거래도 반복 소비 횟수·금액에 넣고 '그 외'로 센다")
    void repeatWithUnnamed() {
        List<Transaction> history = new ArrayList<>();
        for (int day = 1; day <= 3; day++) {
            history.add(spent(CAFE, "스타벅스", 10, day, 5_000));
        }
        history.add(spent(CAFE, null, 10, 10, 3_000));

        assertThat(finder.find(history, PERIOD_FROM))
                .containsExactly(new Recurring("REPEAT", "카페/간식", "4회 · 스타벅스 3회, 그 외 1회", 18_000));
    }

    @Test
    @DisplayName("가맹점 이름이 있는 거래가 없으면 반복 소비 요약은 횟수만 보여준다")
    void repeatAllUnnamed() {
        List<Transaction> history = new ArrayList<>();
        for (int day = 1; day <= 4; day++) {
            history.add(spent(CAFE, null, 10, day, 5_000));
        }

        assertThat(finder.find(history, PERIOD_FROM))
                .containsExactly(new Recurring("REPEAT", "카페/간식", "4회", 20_000));
    }

    @Test
    @DisplayName("3번까지는 반복 소비가 아니고, 기간 앞 거래는 세지 않는다")
    void notRepeat() {
        List<Transaction> history = new ArrayList<>(List.of(spent(CAFE, "스타벅스", 9, 28, 5_000)));
        for (int day = 1; day <= 3; day++) {
            history.add(spent(CAFE, "스타벅스", 10, day, 5_000));
        }

        assertThat(finder.find(history, PERIOD_FROM)).isEmpty();
    }

    @Test
    @DisplayName("4번 이상이어도 기간 전체 결제 횟수의 20%가 안 되면 반복 소비가 아니다")
    void belowShare() {
        List<Transaction> history = new ArrayList<>();
        for (int day = 1; day <= 4; day++) {
            history.add(spent(CAFE, "스타벅스", 10, day, 5_000));
        }
        // 카페 4번 / 전체 21번 = 19%
        for (int i = 0; i < 17; i++) {
            history.add(spent(null, "가게" + i, 10, 20, 1_000));
        }

        assertThat(finder.find(history, PERIOD_FROM)).isEmpty();

        history.removeLast(); // 4 / 20 = 20%
        assertThat(finder.find(history, PERIOD_FROM)).extracting(Recurring::kind).containsExactly("REPEAT");
    }

    @Test
    @DisplayName("교통 같은 필수 지출은 자주 써도 반복 소비가 아니다")
    void essentialCategory() {
        List<Transaction> history = new ArrayList<>();
        for (int day = 1; day <= 10; day++) {
            history.add(spent(TRANSPORT, "서울교통공사", 10, day, 1_500));
        }

        assertThat(finder.find(history, PERIOD_FROM)).isEmpty();
    }

    @Test
    @DisplayName("구독료에 가맹점이 2곳 이상이면 중복 구독이고, 고정지출로 다시 나오지 않는다")
    void duplicateSubscription() {
        List<Transaction> history = new ArrayList<>();
        for (int month = 7; month <= 10; month++) {
            history.add(spent(SUBSCRIPTION, "넷플릭스", month, 5, 17_000));
        }
        history.add(spent(SUBSCRIPTION, "티빙", 10, 12, 10_000));

        assertThat(finder.find(history, PERIOD_FROM))
                .containsExactly(new Recurring("DUPLICATE_SUBSCRIPTION", "구독료", "넷플릭스 · 티빙 같이 구독 중", 27_000));
    }

    @Test
    @DisplayName("고정지출은 이번 기간에 낸 것만 받는 곳 이름으로 보여준다")
    void fixed() {
        List<Transaction> paidThisMonth = new ArrayList<>();
        List<Transaction> notYet = new ArrayList<>();
        for (int month = 7; month <= 10; month++) {
            paidThisMonth.add(spent(TELECOM, "SKT", month, 15, 55_000));
            if (month < 10) {
                notYet.add(spent(TELECOM, "SKT", month, 15, 55_000));
            }
        }

        assertThat(finder.find(paidThisMonth, PERIOD_FROM))
                .containsExactly(new Recurring("FIXED", "SKT", "매월 15일", 55_000));
        assertThat(finder.find(notYet, PERIOD_FROM)).isEmpty();
    }
}
