package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.service.RecurringPaymentDetector.RecurringPayment;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringPaymentDetectorTest {

    private final RecurringPaymentDetector detector = new RecurringPaymentDetector();
    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
    private final LinkedAccount bank = LinkedAccount.builder().user(user).institutionCode("0088")
            .type(LinkedAccountType.BANK).connectedId("cid-1").build();

    {
        ReflectionTestUtils.setField(bank, "id", 3L);
    }

    private Transaction out(String payee, int month, int day, long amount) {
        return Transaction.builder().user(user).linkedAccount(bank).occurredAt(LocalDateTime.of(2026, month, day, 9, 0))
                .amount(amount).direction(TransactionDirection.OUT).merchantOrPayee(payee)
                .source(TransactionSource.CODEF).status(TransactionStatus.CONFIRMED).build();
    }

    @Test
    @DisplayName("석 달 연속 같은 날 같은 금액이 나가면 고정지출 후보다")
    void detectsMonthlyRent() {
        List<RecurringPayment> found = detector.detect(List.of(
                out("김*수", 8, 25, 600_000), out("김*수", 9, 25, 600_000), out("김*수", 10, 25, 600_000)));

        assertThat(found).containsExactly(new RecurringPayment("김*수", 25, 600_000, 600_000, 600_000, 3,
                LocalDate.of(2026, 10, 25), 3L));
    }

    @Test
    @DisplayName("금액 10%·날짜 3일 안의 흔들림은 같은 고정지출로 보고, 가장 최근 금액을 납부액으로 쓴다")
    void toleratesSmallDifferences() {
        List<RecurringPayment> found = detector.detect(List.of(
                out("SKT통신요금", 8, 15, 55_000), out("SKT통신요금", 9, 17, 58_000), out("SKT통신요금", 10, 13, 53_000)));

        assertThat(found).singleElement().satisfies(payment -> {
            assertThat(payment.payDay()).isEqualTo(15);
            assertThat(payment.payAmount()).isEqualTo(53_000);
            assertThat(payment.minAmount()).isEqualTo(53_000);
            assertThat(payment.maxAmount()).isEqualTo(58_000);
        });
    }

    @Test
    @DisplayName("두 달뿐이거나, 금액이 10% 넘게 다르거나, 날짜가 3일 넘게 다르면 후보가 아니다")
    void rejectsOutsideRules() {
        assertThat(detector.detect(List.of(out("넷플릭스", 9, 5, 13_500), out("넷플릭스", 10, 5, 13_500)))).isEmpty();
        assertThat(detector.detect(List.of(out("관리비", 8, 10, 100_000), out("관리비", 9, 10, 150_000),
                out("관리비", 10, 10, 200_000)))).isEmpty();
        assertThat(detector.detect(List.of(out("헬스장", 8, 1, 50_000), out("헬스장", 9, 10, 50_000),
                out("헬스장", 10, 20, 50_000)))).isEmpty();
    }

    @Test
    @DisplayName("같은 금액이 한 달에 여러 번 나가는 곳(자주 가는 카페)은 고정지출이 아니다")
    void rejectsFrequentMerchant() {
        List<Transaction> coffee = new ArrayList<>();
        for (int month = 8; month <= 10; month++) {
            for (int day = 1; day <= 28; day += 3) {
                coffee.add(out("스타벅스", month, day, 4_500));
            }
        }

        assertThat(detector.detect(coffee)).isEmpty();
    }

    @Test
    @DisplayName("월말·월초에 걸친 납부일(30일, 1일, 31일)도 같은 고정지출로 본다")
    void handlesMonthBoundary() {
        List<RecurringPayment> found = detector.detect(List.of(
                out("보험료", 7, 30, 30_000), out("보험료", 9, 1, 30_000), out("보험료", 10, 31, 30_000)));

        assertThat(found).hasSize(1);
        // 달 길이를 31일로 보고 잰다: 30일 ↔ 1일은 2일, 31일 ↔ 1일은 1일
        assertThat(RecurringPaymentDetector.dayGap(1, 30)).isEqualTo(2);
        assertThat(RecurringPaymentDetector.dayGap(1, 31)).isEqualTo(1);
    }

    @Test
    @DisplayName("띄어쓰기만 다른 이름은 같은 곳으로 묶고, 입금·취소·소비 제외 거래는 보지 않는다")
    void normalizesNameAndSkipsNonSpending() {
        Transaction canceled = out("멜론", 10, 3, 10_900);
        canceled.cancel();
        Transaction excluded = out("카드대금", 10, 14, 500_000);
        excluded.excludeFromSpending();
        Transaction income = Transaction.builder().user(user).linkedAccount(bank)
                .occurredAt(LocalDateTime.of(2026, 10, 25, 9, 0)).amount(3_000_000).direction(TransactionDirection.IN)
                .merchantOrPayee("급여").source(TransactionSource.CODEF).status(TransactionStatus.CONFIRMED).build();

        List<RecurringPayment> found = detector.detect(List.of(
                out("멜론 스트리밍", 8, 3, 10_900), out("멜론스트리밍", 9, 3, 10_900), out("멜론 스트리밍", 10, 3, 10_900),
                canceled, excluded, out("카드대금", 8, 14, 500_000), out("카드대금", 9, 14, 500_000), income));

        assertThat(found).extracting(RecurringPayment::payee).containsExactly("멜론 스트리밍");
    }
}
