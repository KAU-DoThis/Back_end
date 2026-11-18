package com.dothis.fintris.dutchpay.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.dutchpay.code.DutchPayErrorCode;
import com.dothis.fintris.dutchpay.dto.DutchPayRequest;
import com.dothis.fintris.dutchpay.dto.DutchPayResponse;
import com.dothis.fintris.dutchpay.entity.DutchPay;
import com.dothis.fintris.dutchpay.entity.DutchPayTransaction;
import com.dothis.fintris.dutchpay.repository.DutchPayRepository;
import com.dothis.fintris.dutchpay.repository.DutchPayTransactionRepository;
import com.dothis.fintris.global.api.code.BaseErrorCode;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.code.TransactionErrorCode;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DutchPayServiceTest {

    private static final long USER_ID = 1L;

    @Mock DutchPayRepository dutchPayRepository;
    @Mock DutchPayTransactionRepository dutchPayTransactionRepository;
    @Mock TransactionRepository transactionRepository;
    @Spy DutchPayShareCalculator shareCalculator;
    @InjectMocks DutchPayService service;

    private final User user = user(USER_ID);
    private final Block living = block();

    private static User user(long id) {
        User user = User.builder().email("user" + id + "@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Block block() {
        Block block = Block.builder().user(user).type(BlockType.SPENDING).name("생활비").balance(100_000L)
                .isDefault(true).startDate(LocalDate.of(2026, 11, 1)).displayOrder(1).build();
        ReflectionTestUtils.setField(block, "id", 10L);
        return block;
    }

    private Transaction transaction(long id, User owner, long amount, TransactionDirection direction, Block block) {
        Transaction transaction = Transaction.builder().user(owner).occurredAt(LocalDateTime.of(2026, 11, 15, 19, 0))
                .amount(amount).direction(direction).source(TransactionSource.CODEF)
                .status(TransactionStatus.CONFIRMED).block(block).build();
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private void given(List<Transaction> payments, List<Transaction> received) {
        when(transactionRepository.findAllById(payments.stream().map(Transaction::getId).toList())).thenReturn(payments);
        if (!received.isEmpty()) {
            when(transactionRepository.findAllById(received.stream().map(Transaction::getId).toList())).thenReturn(received);
        } else {
            when(transactionRepository.findAllById(List.of())).thenReturn(List.of());
        }
    }

    private void givenSaved() {
        when(dutchPayTransactionRepository.findAllByTransaction_IdIn(anyList())).thenReturn(List.of());
        when(dutchPayRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static DutchPayRequest request(List<Long> payments, List<Long> received) {
        return new DutchPayRequest(payments, received);
    }

    private static BaseErrorCode codeOf(Throwable e) {
        return ((GeneralException) e).getCode();
    }

    @Test
    @DisplayName("받은 돈을 결제 비율대로 빼서 내 몫을 남기고, 가장 큰 결제의 블록에 받은 돈을 더한다")
    void create() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        Transaction cafe = transaction(2L, user, 10_000L, TransactionDirection.OUT, null);
        Transaction friendA = transaction(3L, user, 15_000L, TransactionDirection.IN, null);
        Transaction friendB = transaction(4L, user, 5_000L, TransactionDirection.IN, null);
        given(List.of(food, cafe), List.of(friendA, friendB));
        givenSaved();

        DutchPayResponse response = service.create(USER_ID, request(List.of(1L, 2L), List.of(3L, 4L)));

        assertThat(response.payments()).extracting(DutchPayResponse.Payment::myShare).containsExactly(15_000L, 5_000L);
        assertThat(response.myShareTotal()).isEqualTo(20_000L);
        assertThat(response.returnedBlock().amount()).isEqualTo(20_000L);
        assertThat(living.getBalance()).isEqualTo(120_000L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DutchPayTransaction>> links = ArgumentCaptor.forClass(List.class);
        verify(dutchPayTransactionRepository).saveAll(links.capture());
        assertThat(links.getValue()).extracting(DutchPayTransaction::getMyShare)
                .containsExactly(15_000L, 5_000L, null, null);
    }

    @Test
    @DisplayName("받은 돈이 결제보다 많으면 블록에는 결제 합까지만 더한다")
    void receivedMoreThanPaid() {
        Transaction food = transaction(1L, user, 40_000L, TransactionDirection.OUT, living);
        Transaction friend = transaction(3L, user, 45_000L, TransactionDirection.IN, null);
        given(List.of(food), List.of(friend));
        givenSaved();

        DutchPayResponse response = service.create(USER_ID, request(List.of(1L), List.of(3L)));

        assertThat(response.myShareTotal()).isZero();
        assertThat(response.receivedTotal()).isEqualTo(45_000L);
        assertThat(response.returnedBlock().amount()).isEqualTo(40_000L);
        assertThat(living.getBalance()).isEqualTo(140_000L);
    }

    @Test
    @DisplayName("가장 큰 결제가 블록에 반영되지 않았으면 받은 돈을 어느 블록에도 더하지 않는다")
    void largestNotReflected() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, null);
        Transaction cafe = transaction(2L, user, 10_000L, TransactionDirection.OUT, living);
        Transaction friend = transaction(3L, user, 20_000L, TransactionDirection.IN, null);
        given(List.of(food, cafe), List.of(friend));
        givenSaved();

        DutchPayResponse response = service.create(USER_ID, request(List.of(1L, 2L), List.of(3L)));

        assertThat(response.returnedBlock()).isNull();
        assertThat(living.getBalance()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("아직 받은 돈이 없으면 결제 금액이 그대로 내 몫이다")
    void nothingReceived() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        given(List.of(food), List.of());
        givenSaved();

        DutchPayResponse response = service.create(USER_ID, request(List.of(1L), List.of()));

        assertThat(response.myShareTotal()).isEqualTo(30_000L);
        assertThat(response.returnedBlock()).isNull();
        assertThat(living.getBalance()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("이미 다른 더치페이에 들어간 거래가 있으면 막는다")
    void alreadyLinked() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        given(List.of(food), List.of());
        when(dutchPayTransactionRepository.findAllByTransaction_IdIn(anyList()))
                .thenReturn(List.of(DutchPayTransaction.builder().dutchPay(DutchPay.builder().user(user).build())
                        .transaction(food).myShare(30_000L).build()));

        assertThatThrownBy(() -> service.create(USER_ID, request(List.of(1L), List.of())))
                .extracting(DutchPayServiceTest::codeOf).isEqualTo(DutchPayErrorCode.DUTCH_PAY_ALREADY_LINKED);
    }

    @Test
    @DisplayName("결제 자리에 입금을 고르면 막는다")
    void paymentMustBeOutgoing() {
        Transaction deposit = transaction(1L, user, 30_000L, TransactionDirection.IN, null);
        when(transactionRepository.findAllById(List.of(1L))).thenReturn(List.of(deposit));

        assertThatThrownBy(() -> service.create(USER_ID, request(List.of(1L), List.of())))
                .extracting(DutchPayServiceTest::codeOf).isEqualTo(DutchPayErrorCode.DUTCH_PAY_INVALID_PAYMENT);
    }

    @Test
    @DisplayName("받은 돈 자리에 출금을 고르면 막는다")
    void receivedMustBeIncoming() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        Transaction other = transaction(2L, user, 10_000L, TransactionDirection.OUT, living);
        given(List.of(food), List.of(other));

        assertThatThrownBy(() -> service.create(USER_ID, request(List.of(1L), List.of(2L))))
                .extracting(DutchPayServiceTest::codeOf).isEqualTo(DutchPayErrorCode.DUTCH_PAY_INVALID_RECEIVED);
    }

    @Test
    @DisplayName("같은 거래를 두 번 고르면 막는다")
    void duplicate() {
        assertThatThrownBy(() -> service.create(USER_ID, request(List.of(1L), List.of(1L))))
                .extracting(DutchPayServiceTest::codeOf).isEqualTo(DutchPayErrorCode.DUTCH_PAY_DUPLICATE_TRANSACTION);
    }

    @Test
    @DisplayName("취소된 거래는 고를 수 없다")
    void canceled() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        food.cancel();
        when(transactionRepository.findAllById(List.of(1L))).thenReturn(List.of(food));

        assertThatThrownBy(() -> service.create(USER_ID, request(List.of(1L), List.of())))
                .extracting(DutchPayServiceTest::codeOf).isEqualTo(DutchPayErrorCode.DUTCH_PAY_CANCELED_TRANSACTION);
    }

    @Test
    @DisplayName("다른 사용자의 거래는 고를 수 없다")
    void otherUser() {
        Transaction others = transaction(1L, user(2L), 30_000L, TransactionDirection.OUT, null);
        when(transactionRepository.findAllById(List.of(1L))).thenReturn(List.of(others));

        assertThatThrownBy(() -> service.create(USER_ID, request(List.of(1L), List.of())))
                .extracting(DutchPayServiceTest::codeOf).isEqualTo(TransactionErrorCode.TRANSACTION_FORBIDDEN);
    }
}
