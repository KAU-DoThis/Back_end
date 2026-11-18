package com.dothis.fintris.dutchpay.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.dutchpay.entity.DutchPay;
import com.dothis.fintris.dutchpay.entity.DutchPayTransaction;
import com.dothis.fintris.dutchpay.repository.DutchPayRepository;
import com.dothis.fintris.dutchpay.repository.DutchPayTransactionRepository;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DutchPayRecalculatorTest {

    @Mock DutchPayRepository dutchPayRepository;
    @Mock DutchPayTransactionRepository dutchPayTransactionRepository;
    private DutchPayRecalculator recalculator;

    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
    private final Block living = block(10L, "생활비");
    private final Block travel = block(11L, "여행");
    private final DutchPay dutchPay = DutchPay.builder().user(user).build();

    // 결제 30,000원(생활비)에 15,000원을 받아 생활비에 15,000원을 돌려놓은 상태에서 시작한다
    private Transaction food;
    private Transaction friend;
    private DutchPayTransaction paymentLink;
    private DutchPayTransaction receivedLink;

    @BeforeEach
    void setUp() {
        recalculator = new DutchPayRecalculator(dutchPayRepository, dutchPayTransactionRepository, new DutchPayShareCalculator());
        ReflectionTestUtils.setField(dutchPay, "id", 7L);
        food = transaction(1L, 30_000L, TransactionDirection.OUT, living);
        friend = transaction(2L, 15_000L, TransactionDirection.IN, null);
        paymentLink = DutchPayTransaction.builder().dutchPay(dutchPay).transaction(food)
                .myShare(15_000L).block(living).returnedAmount(15_000L).build();
        receivedLink = DutchPayTransaction.builder().dutchPay(dutchPay).transaction(friend).build();
        living.deposit(15_000L);
    }

    private Block block(long id, String name) {
        Block block = Block.builder().user(user).type(BlockType.SPENDING).name(name).balance(100_000L)
                .isDefault(id == 10L).startDate(LocalDate.of(2026, 11, 1)).displayOrder((int) id).build();
        ReflectionTestUtils.setField(block, "id", id);
        return block;
    }

    private Transaction transaction(long id, long amount, TransactionDirection direction, Block block) {
        Transaction transaction = Transaction.builder().user(user).occurredAt(LocalDateTime.of(2026, 11, 15, 19, 0))
                .amount(amount).direction(direction).source(TransactionSource.CODEF)
                .status(TransactionStatus.CONFIRMED).block(block).build();
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private void givenLinked(Transaction transaction, DutchPayTransaction link) {
        when(dutchPayTransactionRepository.findByTransaction_Id(transaction.getId())).thenReturn(Optional.of(link));
        when(dutchPayTransactionRepository.findAllByDutchPay_Id(7L)).thenReturn(List.of(paymentLink, receivedLink));
    }

    @Test
    @DisplayName("결제의 블록이 바뀌면(카테고리 수정) 돌려놓은 돈도 새 블록으로 옮긴다")
    void blockMoved() {
        food.reflectTo(travel, 30_000L);
        givenLinked(food, paymentLink);

        recalculator.recalculateFor(food);

        assertThat(living.getBalance()).isEqualTo(100_000L);
        assertThat(travel.getBalance()).isEqualTo(115_000L);
        assertThat(paymentLink.getBlock()).isSameAs(travel);
    }

    @Test
    @DisplayName("부분취소로 결제 금액이 줄면 내 몫과 돌려놓은 돈을 다시 계산한다")
    void partialCancel() {
        food.changeAmount(20_000L);
        givenLinked(food, paymentLink);

        recalculator.recalculateFor(food);

        assertThat(paymentLink.getMyShare()).isEqualTo(5_000L);
        assertThat(paymentLink.getReturnedAmount()).isEqualTo(15_000L);
        assertThat(living.getBalance()).isEqualTo(115_000L);
    }

    @Test
    @DisplayName("받은 돈이 줄어든 결제보다 많아지면 결제 금액까지만 돌려놓는다")
    void partialCancelBelowReceived() {
        food.changeAmount(10_000L);
        givenLinked(food, paymentLink);

        recalculator.recalculateFor(food);

        assertThat(paymentLink.getMyShare()).isZero();
        assertThat(living.getBalance()).isEqualTo(110_000L);
    }

    @Test
    @DisplayName("결제가 취소되면 0원으로 보고 돌려놓은 돈을 되돌린다")
    void canceled() {
        food.cancel();
        givenLinked(food, paymentLink);

        recalculator.recalculateFor(food);

        assertThat(paymentLink.getMyShare()).isZero();
        assertThat(paymentLink.getReturnedAmount()).isNull();
        assertThat(living.getBalance()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("더치페이에 묶이지 않은 거래는 아무것도 하지 않는다")
    void notLinked() {
        when(dutchPayTransactionRepository.findByTransaction_Id(1L)).thenReturn(Optional.empty());

        recalculator.recalculateFor(food);

        assertThat(living.getBalance()).isEqualTo(115_000L);
    }

    @Test
    @DisplayName("받은 돈 거래를 지우면 묶음에서 빼고 남은 거래로 다시 계산한다")
    void unlinkReceived() {
        givenLinked(friend, receivedLink);

        recalculator.unlink(friend);

        assertThat(paymentLink.getMyShare()).isEqualTo(30_000L);
        assertThat(living.getBalance()).isEqualTo(100_000L);
        verify(dutchPayTransactionRepository).deleteAll(List.of(receivedLink));
    }

    @Test
    @DisplayName("마지막 결제를 지우면 더치페이를 해제한다")
    void unlinkLastPayment() {
        givenLinked(food, paymentLink);

        recalculator.unlink(food);

        assertThat(living.getBalance()).isEqualTo(100_000L);
        verify(dutchPayRepository).delete(dutchPay);
    }

    @Test
    @DisplayName("블록이 모자라면 되돌릴 때 0에서 멈춘다")
    void undoStopsAtZero() {
        living.withdraw(living.getBalance() - 5_000L);

        recalculator.dissolve(dutchPay, List.of(paymentLink, receivedLink));

        assertThat(living.getBalance()).isZero();
    }
}
