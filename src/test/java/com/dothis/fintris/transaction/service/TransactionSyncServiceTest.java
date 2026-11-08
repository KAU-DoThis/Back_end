package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountStatus;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.exception.AccountLinkRejectedException;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.codef.client.CodefBankTransactionClient;
import com.dothis.fintris.codef.client.CodefBankTransactionClient.BankTransaction;
import com.dothis.fintris.codef.client.CodefBankTransactionClient.BankTransactionsResult;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionSyncServiceTest {

    private static final String KOOKMIN = "0004";
    private static final String ACCOUNT_NUMBER = "1002440001234";
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 10, 1, 9, 30, 15);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 10, 2, 18, 0, 0);

    @Mock TransactionRepository transactionRepository;
    @Mock LinkedAccountRepository linkedAccountRepository;
    @Mock CodefBankTransactionClient codefBankTransactionClient;
    @InjectMocks TransactionSyncService service;

    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();

    {
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    private LinkedAccount bankAccount(LocalDateTime lastSyncedAt) {
        LinkedAccount account = LinkedAccount.builder().user(user).institutionCode(KOOKMIN)
                .type(LinkedAccountType.BANK).connectedId("cid-1").accountNumber(ACCOUNT_NUMBER)
                .lastSyncedAt(lastSyncedAt).build();
        ReflectionTestUtils.setField(account, "id", 7L);
        return account;
    }

    // 은행 적요(desc3)가 가맹점·수취인이 된다 (v5)
    private BankTransaction out(LocalDateTime at, long amount, Long balance, String desc3) {
        return new BankTransaction(at, TransactionDirection.OUT, amount, balance, null, null, desc3, null);
    }

    private void givenFetched(BankTransaction... transactions) {
        when(codefBankTransactionClient.fetchTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new BankTransactionsResult(List.of(transactions), null, null, null));
    }

    private List<Transaction> savedTransactions() {
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.captor();
        verify(transactionRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("처음 동기화는 최근 3개월을 조회해 거래를 CODEF 확정 거래로 저장한다")
    void initialSyncFetchesThreeMonths() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        givenFetched(out(T1, 4_500, 120_000L, "스타벅스"));

        int count = service.syncAccount(bankAccount(null));

        verify(codefBankTransactionClient).fetchTransactions("cid-1", KOOKMIN, ACCOUNT_NUMBER,
                today.minusMonths(3), today);
        assertThat(count).isEqualTo(1);
        Transaction saved = savedTransactions().get(0);
        assertThat(saved.getAmount()).isEqualTo(4_500L);
        assertThat(saved.getDirection()).isEqualTo(TransactionDirection.OUT);
        assertThat(saved.getMerchantOrPayee()).isEqualTo("스타벅스");
        assertThat(saved.getBalanceAfter()).isEqualTo(120_000L);
        assertThat(saved.getSource()).isEqualTo(TransactionSource.CODEF);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(saved.getExternalKey()).isEqualTo(TransactionSyncService.bankExternalKey(7L, T1, 4_500, "스타벅스"));
    }

    @Test
    @DisplayName("이후 동기화는 마지막 동기화 하루 전부터 조회한다")
    void incrementalSyncStartsOneDayBeforeLastSync() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        givenFetched();

        service.syncAccount(bankAccount(LocalDateTime.of(2026, 10, 20, 6, 0)));

        verify(codefBankTransactionClient).fetchTransactions("cid-1", KOOKMIN, ACCOUNT_NUMBER,
                LocalDate.of(2026, 10, 19), today);
    }

    @Test
    @DisplayName("이미 저장된 거래는 다시 저장하지 않는다")
    void skipsAlreadySavedTransactions() {
        givenFetched(out(T1, 4_500, 120_000L, "스타벅스"), out(T2, 8_000, 112_000L, "버스"));
        when(transactionRepository.findExistingExternalKeys(eq(1L), anyCollection()))
                .thenReturn(List.of(TransactionSyncService.bankExternalKey(7L, T1, 4_500, "스타벅스")));

        int count = service.syncAccount(bankAccount(null));

        assertThat(count).isEqualTo(1);
        assertThat(savedTransactions()).extracting(Transaction::getMerchantOrPayee).containsExactly("버스");
    }

    @Test
    @DisplayName("같은 초에 금액·적요까지 같은 거래는 번호를 붙여 둘 다 저장한다")
    void keepsIdenticalTransactionsWithSequence() {
        givenFetched(out(T1, 1_000, null, "편의점"), out(T1, 1_000, null, "편의점"));

        service.syncAccount(bankAccount(null));

        String key = TransactionSyncService.bankExternalKey(7L, T1, 1_000, "편의점");
        assertThat(savedTransactions()).extracting(Transaction::getExternalKey).containsExactly(key, key + "#2");
    }

    @Test
    @DisplayName("중복 판정 키는 계좌·일시·금액·적요의 SHA-256 해시이고, 적요가 다르면 키도 다르다")
    void externalKeyIsHashOfAccountTimeAmountAndDescription() {
        String key = TransactionSyncService.bankExternalKey(7L, T1, 1_000, "편의점");

        assertThat(key).hasSize(64).matches("[0-9a-f]+");
        assertThat(TransactionSyncService.bankExternalKey(7L, T1, 1_000, " 편의점 ")).isEqualTo(key);
        assertThat(TransactionSyncService.bankExternalKey(7L, T1, 1_000, "카페")).isNotEqualTo(key);
        assertThat(TransactionSyncService.bankExternalKey(8L, T1, 1_000, "편의점")).isNotEqualTo(key);
    }

    @Test
    @DisplayName("가맹점·수취인은 적요(desc3)만 쓰고, 비어 있으면 null")
    void usesOnlyDesc3AsMerchant() {
        givenFetched(new BankTransaction(T1, TransactionDirection.IN, 300_000, 420_000L, "김철수", "이체", " 급여 ", null),
                new BankTransaction(T2, TransactionDirection.OUT, 5_000, 415_000L, "홍길동", "이체", " ", null));

        service.syncAccount(bankAccount(null));

        assertThat(savedTransactions()).extracting(Transaction::getMerchantOrPayee).containsExactly("급여", null);
    }

    @Test
    @DisplayName("마지막 거래의 잔액으로 계좌 잔액과 동기화 시각을 갱신한다")
    void updatesBalanceAndSyncedAt() {
        LinkedAccount account = bankAccount(null);
        givenFetched(out(T1, 4_500, 120_000L, "스타벅스"), out(T2, 8_000, 112_000L, "버스"));

        service.syncAccount(account);

        assertThat(account.getBalance()).isEqualTo(112_000L);
        assertThat(account.getBalanceUpdatedAt()).isNotNull();
        assertThat(account.getLastSyncedAt()).isNotNull();
        verify(linkedAccountRepository).save(account);
    }

    @Test
    @DisplayName("새 거래가 없어도 동기화 시각은 갱신하고 기존 잔액은 그대로 둔다")
    void keepsBalanceWhenNothingFetched() {
        LinkedAccount account = bankAccount(null);
        ReflectionTestUtils.setField(account, "balance", 50_000L);
        givenFetched();

        int count = service.syncAccount(account);

        assertThat(count).isZero();
        assertThat(account.getBalance()).isEqualTo(50_000L);
        assertThat(account.getLastSyncedAt()).isNotNull();
    }

    @Test
    @DisplayName("응답의 현재 잔액이 있으면 거래가 없어도 그 값으로 잔액을 갱신한다")
    void updatesBalanceFromCurrentBalanceWithoutTransactions() {
        LinkedAccount account = bankAccount(null);
        ReflectionTestUtils.setField(account, "balance", 50_000L);
        when(codefBankTransactionClient.fetchTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new BankTransactionsResult(List.of(), 0L, null, null));

        service.syncAccount(account);

        assertThat(account.getBalance()).isZero();
        assertThat(account.getBalanceUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("현재 잔액과 마지막 거래 잔액이 다르면 현재 잔액을 쓴다")
    void prefersCurrentBalanceOverLastTransactionBalance() {
        LinkedAccount account = bankAccount(null);
        when(codefBankTransactionClient.fetchTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new BankTransactionsResult(
                        List.of(out(T1, 4_500, 120_000L, "스타벅스")), 99_000L, null, null));

        service.syncAccount(account);

        assertThat(account.getBalance()).isEqualTo(99_000L);
    }

    @Test
    @DisplayName("CODEF가 조회를 거절하면 사유를 전달하고 아무것도 저장하지 않는다")
    void deliversFailureWithoutSaving() {
        LinkedAccount account = bankAccount(null);
        when(codefBankTransactionClient.fetchTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new BankTransactionsResult(List.of(), null, "CF-12801", "조회에 실패했습니다."));

        assertThatThrownBy(() -> service.syncAccount(account))
                .isInstanceOfSatisfying(AccountLinkRejectedException.class,
                        e -> assertThat(e.getInstitutionCode()).isEqualTo("CF-12801"));
        verify(transactionRepository, never()).saveAll(any());
        assertThat(account.getLastSyncedAt()).isNull();
    }

    private void givenFailure(String code) {
        when(codefBankTransactionClient.fetchTransactions(any(), any(), any(), any(), any()))
                .thenReturn(new BankTransactionsResult(List.of(), null, code, "실패"));
    }

    @Test
    @DisplayName("인증 문제로 실패하면 계좌를 EXPIRED로 표시한다")
    void marksExpiredOnAuthFailure() {
        LinkedAccount account = bankAccount(null);
        givenFailure("CF-12801");

        assertThatThrownBy(() -> service.syncAccount(account)).isInstanceOf(AccountLinkRejectedException.class);

        assertThat(account.getStatus()).isEqualTo(LinkedAccountStatus.EXPIRED);
        verify(linkedAccountRepository).save(account);
    }

    @Test
    @DisplayName("그 밖의 실패는 ERROR로 표시한다")
    void marksErrorOnOtherFailure() {
        LinkedAccount account = bankAccount(null);
        givenFailure("CF-09999");

        assertThatThrownBy(() -> service.syncAccount(account)).isInstanceOf(AccountLinkRejectedException.class);

        assertThat(account.getStatus()).isEqualTo(LinkedAccountStatus.ERROR);
    }

    @Test
    @DisplayName("이미 같은 상태면 다시 저장하지 않는다")
    void doesNotSaveWhenStatusUnchanged() {
        LinkedAccount account = bankAccount(null);
        account.markSyncFailed(LinkedAccountStatus.EXPIRED);
        givenFailure("CF-12801");

        assertThatThrownBy(() -> service.syncAccount(account)).isInstanceOf(AccountLinkRejectedException.class);

        verify(linkedAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("동기화에 성공하면 이전의 만료 상태가 ACTIVE로 돌아간다")
    void successRestoresActive() {
        LinkedAccount account = bankAccount(null);
        account.markSyncFailed(LinkedAccountStatus.EXPIRED);
        givenFetched();

        service.syncAccount(account);

        assertThat(account.getStatus()).isEqualTo(LinkedAccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("카드는 거래내역을 조회하지 않는다")
    void skipsCard() {
        LinkedAccount card = LinkedAccount.builder().user(user).institutionCode("0306")
                .type(LinkedAccountType.CARD).connectedId("cid-1").build();

        assertThat(service.syncAccount(card)).isZero();
        verifyNoInteractions(codefBankTransactionClient, transactionRepository);
    }

    @Test
    @DisplayName("사용자 동기화는 한 계좌가 실패해도 나머지를 계속하고 실패 수를 센다")
    void syncUserIsolatesFailures() {
        LinkedAccount failing = bankAccount(null);
        LinkedAccount working = LinkedAccount.builder().user(user).institutionCode("0088")
                .type(LinkedAccountType.BANK).connectedId("cid-1").accountNumber("110123456789").build();
        ReflectionTestUtils.setField(working, "id", 8L);
        LinkedAccount card = LinkedAccount.builder().user(user).institutionCode("0306")
                .type(LinkedAccountType.CARD).connectedId("cid-1").build();
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(failing, working, card));
        when(codefBankTransactionClient.fetchTransactions(eq("cid-1"), eq(KOOKMIN), any(), any(), any()))
                .thenReturn(new BankTransactionsResult(List.of(), null, "CF-12801", "조회에 실패했습니다."));
        when(codefBankTransactionClient.fetchTransactions(eq("cid-1"), eq("0088"), any(), any(), any()))
                .thenReturn(new BankTransactionsResult(List.of(), null, null, null));

        SyncSummary summary = service.syncUser(1L);

        assertThat(summary).isEqualTo(new SyncSummary(2, 1));
        assertThat(working.getLastSyncedAt()).isNotNull();
    }
}
