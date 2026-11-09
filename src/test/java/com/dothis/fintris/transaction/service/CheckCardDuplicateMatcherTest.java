package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckCardDuplicateMatcherTest {

    private final CheckCardDuplicateMatcher matcher = new CheckCardDuplicateMatcher();
    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
    private final LinkedAccount bankAccount = LinkedAccount.builder().user(user).institutionCode("0088")
            .type(LinkedAccountType.BANK).connectedId("cid-1").build();
    private final LinkedAccount cardAccount = LinkedAccount.builder().user(user).institutionCode("0306")
            .type(LinkedAccountType.CARD).connectedId("cid-2").build();

    private Transaction out(LinkedAccount account, LocalDateTime at, long amount) {
        return Transaction.builder().user(user).linkedAccount(account).occurredAt(at).amount(amount)
                .direction(TransactionDirection.OUT).source(TransactionSource.CODEF)
                .status(TransactionStatus.CONFIRMED).build();
    }

    private Transaction bank(LocalDateTime at, long amount) {
        return out(bankAccount, at, amount);
    }

    private Transaction card(LocalDateTime at, long amount) {
        return out(cardAccount, at, amount);
    }

    private static LocalDateTime at(int day, int hour, int minute, int second) {
        return LocalDateTime.of(2026, 11, day, hour, minute, second);
    }

    @Test
    @DisplayName("같은 날·같은 금액이고 1분 이내인 은행 출금을 카드 승인의 중복으로 본다")
    void matchesWithinOneMinute() {
        Transaction bank = bank(at(7, 9, 30, 17), 4_500);

        assertThat(matcher.findBankDuplicates(List.of(card(at(7, 9, 30, 15), 4_500), bank))).containsExactly(bank);
    }

    @Test
    @DisplayName("1분이 넘게 차이 나거나 금액·날짜가 다르면 묶지 않는다")
    void doesNotMatchOtherwise() {
        Transaction card = card(at(7, 9, 30, 0), 4_500);

        assertThat(matcher.findBankDuplicates(List.of(card, bank(at(7, 9, 31, 1), 4_500)))).isEmpty();
        assertThat(matcher.findBankDuplicates(List.of(card, bank(at(7, 9, 30, 10), 4_600)))).isEmpty();
        assertThat(matcher.findBankDuplicates(List.of(card, bank(at(8, 9, 30, 0), 4_500)))).isEmpty();
    }

    @Test
    @DisplayName("카드 승인 하나는 은행 출금 하나와만 짝이 되고, 가장 가까운 것끼리 묶는다")
    void matchesOneToOne() {
        Transaction first = bank(at(7, 9, 30, 5), 4_500);
        Transaction second = bank(at(7, 9, 30, 40), 4_500);

        assertThat(matcher.findBankDuplicates(List.of(card(at(7, 9, 30, 0), 4_500), first, second)))
                .containsExactly(first);
        assertThat(matcher.findBankDuplicates(List.of(card(at(7, 9, 30, 0), 4_500), card(at(7, 9, 30, 41), 4_500),
                first, second))).containsExactly(first, second);
    }

    @Test
    @DisplayName("이미 제외된 은행 출금이 쓴 카드 승인으로 다른 은행 출금을 또 빼지 않는다")
    void doesNotReuseCardAlreadyMatched() {
        Transaction alreadyExcluded = bank(at(7, 9, 30, 2), 4_500);
        alreadyExcluded.excludeFromSpending();
        Transaction another = bank(at(7, 9, 30, 30), 4_500);

        assertThat(matcher.findBankDuplicates(List.of(card(at(7, 9, 30, 0), 4_500), alreadyExcluded, another)))
                .isEmpty();
    }

    @Test
    @DisplayName("거래 시각이 없는(자정) 은행 출금은 그날 같은 금액이 카드·은행 모두 한 건일 때만 묶는다")
    void midnightBankMatchesOnlyWhenUnique() {
        Transaction midnight = bank(at(7, 0, 0, 0), 4_500);

        assertThat(matcher.findBankDuplicates(List.of(card(at(7, 15, 0, 0), 4_500), midnight)))
                .containsExactly(midnight);
        assertThat(matcher.findBankDuplicates(List.of(card(at(7, 9, 0, 0), 4_500), card(at(7, 15, 0, 0), 4_500),
                midnight))).isEmpty();
        assertThat(matcher.findBankDuplicates(List.of(card(at(7, 15, 0, 0), 4_500), midnight,
                bank(at(7, 0, 0, 0), 4_500)))).isEmpty();
    }

    @Test
    @DisplayName("카드가 없으면 아무것도 빼지 않고, 계좌가 없는 현금 입력은 건너뛴다")
    void ignoresWithoutCardsAndCash() {
        Transaction cash = out(null, at(7, 9, 30, 0), 4_500);

        assertThat(matcher.findBankDuplicates(List.of(bank(at(7, 9, 30, 0), 4_500)))).isEmpty();
        assertThat(matcher.findBankDuplicates(List.of(card(at(7, 9, 30, 0), 4_500), cash))).isEmpty();
    }
}
