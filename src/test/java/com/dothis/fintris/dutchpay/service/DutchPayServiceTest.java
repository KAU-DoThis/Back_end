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
import com.dothis.fintris.waitingfund.repository.WaitingFundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    @Mock WaitingFundRepository waitingFundRepository;
    private DutchPayService service;

    @BeforeEach
    void setUp() {
        DutchPayRecalculator recalculator = new DutchPayRecalculator(
                dutchPayRepository, dutchPayTransactionRepository, new DutchPayShareCalculator(), waitingFundRepository);
        service = new DutchPayService(dutchPayRepository, dutchPayTransactionRepository, transactionRepository, recalculator);
    }

    private final User user = user(USER_ID);
    private final Block living = block(10L, "생활비", 100_000L);
    private final Block pocket = block(11L, "용돈", 50_000L);

    private static User user(long id) {
        User user = User.builder().email("user" + id + "@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Block block(long id, String name, long balance) {
        Block block = Block.builder().user(user).type(BlockType.SPENDING).name(name).balance(balance)
                .isDefault(id == 10L).startDate(LocalDate.of(2026, 11, 1)).displayOrder((int) id).build();
        ReflectionTestUtils.setField(block, "id", id);
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
        return new DutchPayRequest(payments, received, null);
    }

    private static BaseErrorCode codeOf(Throwable e) {
        return ((GeneralException) e).getCode();
    }

    @Test
    @DisplayName("받은 돈을 결제 비율대로 빼서 내 몫을 남기고, 결제마다 뺀 만큼 그 결제의 블록에 돌려놓는다")
    void create() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        Transaction cafe = transaction(2L, user, 10_000L, TransactionDirection.OUT, pocket);
        Transaction friendA = transaction(3L, user, 15_000L, TransactionDirection.IN, null);
        Transaction friendB = transaction(4L, user, 5_000L, TransactionDirection.IN, null);
        given(List.of(food, cafe), List.of(friendA, friendB));
        givenSaved();

        DutchPayResponse response = service.create(USER_ID, request(List.of(1L, 2L), List.of(3L, 4L)));

        assertThat(response.payments()).extracting(DutchPayResponse.Payment::myShare).containsExactly(15_000L, 5_000L);
        assertThat(response.payments()).extracting(p -> p.returnedBlock().amount()).containsExactly(15_000L, 5_000L);
        assertThat(response.myShareTotal()).isEqualTo(20_000L);
        assertThat(response.returnedTotal()).isEqualTo(20_000L);
        assertThat(living.getBalance()).isEqualTo(115_000L);
        assertThat(pocket.getBalance()).isEqualTo(55_000L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DutchPayTransaction>> links = ArgumentCaptor.forClass(List.class);
        verify(dutchPayTransactionRepository).saveAll(links.capture());
        assertThat(links.getValue()).extracting(DutchPayTransaction::getMyShare)
                .containsExactly(15_000L, 5_000L, null, null);
        assertThat(links.getValue()).extracting(DutchPayTransaction::getReturnedAmount)
                .containsExactly(15_000L, 5_000L, null, null);
    }

    @Test
    @DisplayName("받은 돈이 결제보다 많으면 결제 금액까지만 돌려놓고 넘친 돈은 나눌 돈으로 넣는다")
    void receivedMoreThanPaid() {
        Transaction food = transaction(1L, user, 40_000L, TransactionDirection.OUT, living);
        Transaction friend = transaction(3L, user, 45_000L, TransactionDirection.IN, null);
        given(List.of(food), List.of(friend));
        givenSaved();

        DutchPayResponse response = service.create(USER_ID, request(List.of(1L), List.of(3L)));

        assertThat(response.myShareTotal()).isZero();
        assertThat(response.receivedTotal()).isEqualTo(45_000L);
        assertThat(response.returnedTotal()).isEqualTo(40_000L);
        assertThat(response.bufferAmount()).isEqualTo(5_000L);
        assertThat(living.getBalance()).isEqualTo(140_000L);
    }

    @Test
    @DisplayName("블록에 반영되지 않은 결제 몫은 돌려놓을 곳이 없어 나눌 돈으로 넣는다")
    void unreflectedPaymentSkipped() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, null);
        Transaction cafe = transaction(2L, user, 10_000L, TransactionDirection.OUT, living);
        Transaction friend = transaction(3L, user, 20_000L, TransactionDirection.IN, null);
        given(List.of(food, cafe), List.of(friend));
        givenSaved();

        DutchPayResponse response = service.create(USER_ID, request(List.of(1L, 2L), List.of(3L)));

        assertThat(response.payments().get(0).returnedBlock()).isNull();
        assertThat(response.payments().get(1).returnedBlock().amount()).isEqualTo(5_000L);
        assertThat(response.bufferAmount()).isEqualTo(15_000L);
        assertThat(living.getBalance()).isEqualTo(105_000L);
    }

    @Test
    @DisplayName("내 몫을 직접 정하면 결제 - 내 몫을 돌려놓고, 덜 받은 돈은 아직 받을 돈으로 보여 준다")
    void chosenMyShare() {
        Transaction dinner = transaction(1L, user, 120_000L, TransactionDirection.OUT, living);
        Transaction friend = transaction(3L, user, 60_000L, TransactionDirection.IN, null);
        given(List.of(dinner), List.of(friend));
        givenSaved();

        DutchPayResponse response = service.create(USER_ID,
                new DutchPayRequest(List.of(1L), List.of(3L), 30_000L));

        assertThat(response.myShareTotal()).isEqualTo(30_000L);
        assertThat(response.myShareEdited()).isTrue();
        assertThat(response.returnedTotal()).isEqualTo(60_000L);
        assertThat(response.outstandingAmount()).isEqualTo(30_000L);
        assertThat(response.bufferAmount()).isZero();
        assertThat(living.getBalance()).isEqualTo(160_000L);
    }

    @Test
    @DisplayName("내 몫이 결제 합보다 크면 400")
    void myShareOverPaid() {
        Transaction dinner = transaction(1L, user, 120_000L, TransactionDirection.OUT, living);
        given(List.of(dinner), List.of());
        when(dutchPayTransactionRepository.findAllByTransaction_IdIn(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(USER_ID, new DutchPayRequest(List.of(1L), List.of(), 130_000L)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(DutchPayErrorCode.DUTCH_PAY_INVALID_MY_SHARE));
    }

    @Test
    @DisplayName("아직 받은 돈이 없으면 결제 금액이 그대로 내 몫이다")
    void nothingReceived() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        given(List.of(food), List.of());
        givenSaved();

        DutchPayResponse response = service.create(USER_ID, request(List.of(1L), List.of()));

        assertThat(response.myShareTotal()).isEqualTo(30_000L);
        assertThat(response.returnedTotal()).isZero();
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
    @DisplayName("소비에서 뺀 출금은 결제로 고를 수 없다")
    void paymentMustNotBeExcluded() {
        Transaction duplicate = transaction(1L, user, 30_000L, TransactionDirection.OUT, null);
        duplicate.excludeFromSpending();
        when(transactionRepository.findAllById(List.of(1L))).thenReturn(List.of(duplicate));

        assertThatThrownBy(() -> service.create(USER_ID, request(List.of(1L), List.of())))
                .extracting(DutchPayServiceTest::codeOf).isEqualTo(DutchPayErrorCode.DUTCH_PAY_EXCLUDED_PAYMENT);
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

    @Test
    @DisplayName("묶은 뒤 취소된 결제는 취소됐다고 알려 준다")
    void canceledAfterLinked() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        Transaction friendA = transaction(2L, user, 15_000L, TransactionDirection.IN, null);
        existing(food, friendA);
        food.cancel();

        DutchPayResponse response = service.changeMyShare(USER_ID, 7L, null);

        assertThat(response.payments()).extracting(DutchPayResponse.Payment::canceled).containsExactly(true);
        assertThat(response.received()).extracting(DutchPayResponse.Received::canceled).containsExactly(false);
    }

    // 이미 만들어진 더치페이: 결제 food(30,000, 생활비)와 받은 돈 friendA(15,000)로 생활비에 15,000원을 돌려놓은 상태
    private DutchPay existing(Transaction food, Transaction friendA) {
        DutchPay dutchPay = DutchPay.builder().user(user).build();
        ReflectionTestUtils.setField(dutchPay, "id", 7L);
        DutchPayTransaction payment = DutchPayTransaction.builder().dutchPay(dutchPay).transaction(food)
                .myShare(15_000L).block(living).returnedAmount(15_000L).build();
        DutchPayTransaction received = DutchPayTransaction.builder().dutchPay(dutchPay).transaction(friendA).build();
        when(dutchPayRepository.findForUpdate(7L)).thenReturn(Optional.of(dutchPay));
        when(dutchPayTransactionRepository.findAllByDutchPay_Id(7L)).thenReturn(new ArrayList<>(List.of(payment, received)));
        return dutchPay;
    }

    @Test
    @DisplayName("수정: 나중에 받은 돈을 붙이면 내 몫을 다시 계산하고 늘어난 만큼만 블록에 더 돌려놓는다")
    void updateAddsReceived() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        Transaction friendA = transaction(3L, user, 15_000L, TransactionDirection.IN, null);
        Transaction friendB = transaction(4L, user, 10_000L, TransactionDirection.IN, null);
        living.deposit(15_000L); // 만들 때 돌려놓은 돈
        DutchPay dutchPay = existing(food, friendA);
        given(List.of(food), List.of(friendA, friendB));
        when(dutchPayTransactionRepository.findAllByTransaction_IdIn(anyList())).thenReturn(List.of(
                DutchPayTransaction.builder().dutchPay(dutchPay).transaction(food).build(),
                DutchPayTransaction.builder().dutchPay(dutchPay).transaction(friendA).build()));

        DutchPayResponse response = service.update(USER_ID, 7L, request(List.of(1L), List.of(3L, 4L)));

        assertThat(response.myShareTotal()).isEqualTo(5_000L);
        assertThat(response.returnedTotal()).isEqualTo(25_000L);
        assertThat(living.getBalance()).isEqualTo(125_000L);
    }

    @Test
    @DisplayName("수정: 내 몫을 안 보내면 사용자가 고쳐 둔 내 몫을 그대로 둔다")
    void updateKeepsChosenMyShare() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        Transaction friendA = transaction(3L, user, 15_000L, TransactionDirection.IN, null);
        Transaction friendB = transaction(4L, user, 10_000L, TransactionDirection.IN, null);
        living.deposit(15_000L);
        DutchPay dutchPay = existing(food, friendA);
        dutchPay.changeMyShare(10_000L);
        given(List.of(food), List.of(friendA, friendB));
        when(dutchPayTransactionRepository.findAllByTransaction_IdIn(anyList())).thenReturn(List.of());

        DutchPayResponse response = service.update(USER_ID, 7L, request(List.of(1L), List.of(3L, 4L)));

        assertThat(response.myShareTotal()).isEqualTo(10_000L);
        assertThat(response.myShareEdited()).isTrue();
        assertThat(response.returnedTotal()).isEqualTo(20_000L);
        assertThat(response.bufferAmount()).isEqualTo(5_000L);
        assertThat(living.getBalance()).isEqualTo(120_000L);
    }

    @Test
    @DisplayName("내 몫 수정 버튼: 고친 값으로 다시 계산하고, null이면 서비스가 정한 값으로 되돌린다")
    void changeMyShare() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        Transaction friendA = transaction(3L, user, 15_000L, TransactionDirection.IN, null);
        living.deposit(15_000L);
        existing(food, friendA);

        DutchPayResponse edited = service.changeMyShare(USER_ID, 7L, 20_000L);

        assertThat(edited.myShareTotal()).isEqualTo(20_000L);
        assertThat(edited.myShareEdited()).isTrue();
        assertThat(edited.returnedTotal()).isEqualTo(10_000L);
        assertThat(edited.bufferAmount()).isEqualTo(5_000L);
        assertThat(living.getBalance()).isEqualTo(110_000L);

        DutchPayResponse auto = service.changeMyShare(USER_ID, 7L, null);

        assertThat(auto.myShareTotal()).isEqualTo(15_000L);
        assertThat(auto.myShareEdited()).isFalse();
        assertThat(auto.bufferAmount()).isZero();
        assertThat(living.getBalance()).isEqualTo(115_000L);
    }

    @Test
    @DisplayName("내 몫 수정 버튼: 결제 합보다 크면 400")
    void changeMyShareOverPaid() {
        existing(transaction(1L, user, 30_000L, TransactionDirection.OUT, living),
                transaction(3L, user, 15_000L, TransactionDirection.IN, null));

        assertThatThrownBy(() -> service.changeMyShare(USER_ID, 7L, 40_000L))
                .extracting(DutchPayServiceTest::codeOf).isEqualTo(DutchPayErrorCode.DUTCH_PAY_INVALID_MY_SHARE);
    }

    @Test
    @DisplayName("수정: 받은 돈을 빼면 그만큼 블록에서 다시 뺀다")
    void updateRemovesReceived() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        Transaction friendA = transaction(3L, user, 15_000L, TransactionDirection.IN, null);
        living.deposit(15_000L);
        DutchPay dutchPay = existing(food, friendA);
        given(List.of(food), List.of());
        when(dutchPayTransactionRepository.findAllByTransaction_IdIn(anyList())).thenReturn(List.of(
                DutchPayTransaction.builder().dutchPay(dutchPay).transaction(food).build()));

        DutchPayResponse response = service.update(USER_ID, 7L, request(List.of(1L), List.of()));

        assertThat(response.myShareTotal()).isEqualTo(30_000L);
        assertThat(response.received()).isEmpty();
        assertThat(living.getBalance()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("수정: 다른 더치페이에 들어간 거래를 고르면 막는다")
    void updateLinkedElsewhere() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        Transaction friendA = transaction(3L, user, 15_000L, TransactionDirection.IN, null);
        existing(food, friendA);
        given(List.of(food), List.of(friendA));
        DutchPay other = DutchPay.builder().user(user).build();
        ReflectionTestUtils.setField(other, "id", 8L);
        when(dutchPayTransactionRepository.findAllByTransaction_IdIn(anyList())).thenReturn(List.of(
                DutchPayTransaction.builder().dutchPay(other).transaction(friendA).build()));

        assertThatThrownBy(() -> service.update(USER_ID, 7L, request(List.of(1L), List.of(3L))))
                .extracting(DutchPayServiceTest::codeOf).isEqualTo(DutchPayErrorCode.DUTCH_PAY_ALREADY_LINKED);
    }

    @Test
    @DisplayName("해제: 블록에 돌려놓은 돈을 다시 빼고 묶음을 지운다")
    void delete() {
        Transaction food = transaction(1L, user, 30_000L, TransactionDirection.OUT, living);
        Transaction friendA = transaction(3L, user, 15_000L, TransactionDirection.IN, null);
        living.deposit(15_000L);
        DutchPay dutchPay = existing(food, friendA);

        service.delete(USER_ID, 7L);

        assertThat(living.getBalance()).isEqualTo(100_000L);
        verify(dutchPayRepository).delete(dutchPay);
    }

    @Test
    @DisplayName("다른 사용자의 더치페이는 해제할 수 없다")
    void deleteForbidden() {
        DutchPay others = DutchPay.builder().user(user(2L)).build();
        when(dutchPayRepository.findForUpdate(7L)).thenReturn(Optional.of(others));

        assertThatThrownBy(() -> service.delete(USER_ID, 7L))
                .extracting(DutchPayServiceTest::codeOf).isEqualTo(DutchPayErrorCode.DUTCH_PAY_FORBIDDEN);
    }
}
