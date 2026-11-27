package com.dothis.fintris.dutchpay.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DutchPayShareCalculatorTest {

    private final DutchPayShareCalculator calculator = new DutchPayShareCalculator();

    @Test
    @DisplayName("결제 하나면 받은 돈만큼 뺀 게 내 몫이다")
    void singlePayment() {
        assertThat(calculator.myShares(List.of(40_000L), 30_000L)).containsExactly(10_000L);
    }

    @Test
    @DisplayName("결제가 여러 건이면 받은 돈을 금액 비율대로 나눠 뺀다")
    void proportional() {
        assertThat(calculator.myShares(List.of(30_000L, 10_000L), 20_000L))
                .containsExactly(15_000L, 5_000L);
    }

    @Test
    @DisplayName("나누고 남은 원은 버린 소수점이 큰 결제에서 더 뺀다")
    void remainder() {
        // 10,000원을 1:1:1로 나누면 3,333.33…원씩. 남은 1원은 금액이 같으니 앞 결제에서 뺀다
        assertThat(calculator.myShares(List.of(10_000L, 10_000L, 10_000L), 10_000L))
                .containsExactly(6_666L, 6_667L, 6_667L);
    }

    @Test
    @DisplayName("작은 결제가 많아도 내 몫은 0원 밑으로 가지 않는다")
    void neverNegative() {
        // 1원씩 3건에 2원을 받으면 2건은 0원, 1건은 1원
        List<Long> shares = calculator.myShares(List.of(1L, 1L, 1L), 2L);

        assertThat(shares).allMatch(share -> share >= 0);
        assertThat(shares.stream().mapToLong(Long::longValue).sum()).isEqualTo(1L);
    }

    @Test
    @DisplayName("받은 돈이 결제보다 많으면 내 몫은 모두 0원이다")
    void receivedMoreThanPaid() {
        assertThat(calculator.myShares(List.of(30_000L, 10_000L), 45_000L))
                .containsExactly(0L, 0L);
    }

    @Test
    @DisplayName("받은 돈이 없으면 결제 금액이 그대로 내 몫이다")
    void nothingReceived() {
        assertThat(calculator.myShares(List.of(30_000L, 10_000L), 0L))
                .containsExactly(30_000L, 10_000L);
    }

    @Test
    @DisplayName("내 몫의 합은 결제 합에서 받은 돈을 뺀 값과 같다")
    void sumMatches() {
        List<Long> shares = calculator.myShares(List.of(12_345L, 6_789L, 1_001L), 9_999L);

        assertThat(shares.stream().mapToLong(Long::longValue).sum())
                .isEqualTo(12_345L + 6_789L + 1_001L - 9_999L);
    }
}
