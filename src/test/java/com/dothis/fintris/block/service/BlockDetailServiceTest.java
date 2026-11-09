package com.dothis.fintris.block.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.block.dto.BlockDetailResponse;
import com.dothis.fintris.block.dto.BlockHistoryResponse;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockCategoryRepository;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.repository.BlockSpent;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockDetailServiceTest {

    private static final Long USER_ID = 1L;

    @Mock BlockRepository blockRepository;
    @Mock BlockTransferRepository blockTransferRepository;
    @Mock BlockCategoryRepository blockCategoryRepository;
    @Mock TransactionRepository transactionRepository;
    @InjectMocks BlockDetailService service;

    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

    {
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    private Block given(Block block) {
        ReflectionTestUtils.setField(block, "id", 10L);
        when(blockRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(block));
        when(blockCategoryRepository.findAllByBlock_Id(10L)).thenReturn(List.of());
        return block;
    }

    private Transaction reflected(Block block, LocalDateTime at, long amount, String payee) {
        LinkedAccount bank = LinkedAccount.builder().user(user).institutionCode("0004").type(LinkedAccountType.BANK)
                .connectedId("cid-1").accountNumberLast4("1234").build();
        Transaction transaction = Transaction.builder().user(user).linkedAccount(bank).occurredAt(at).amount(amount)
                .direction(TransactionDirection.OUT).merchantOrPayee(payee).source(TransactionSource.CODEF)
                .status(TransactionStatus.CONFIRMED).build();
        transaction.reflectTo(block, amount);
        return transaction;
    }

    @Test
    @DisplayName("사용형: 오늘 쓴 돈과 최근 거래 5건을 채우고, 오늘 가능 금액은 아침 잔액 기준으로 둔다")
    void spendingToday() {
        Block living = given(Block.builder().user(user).type(BlockType.SPENDING).name("생활비").balance(87_700L)
                .isDefault(true).startDate(today.withDayOfMonth(1)).dueDate(today)
                .rule(new BlockRule(1_500_000L, 1, null, null, null, null, null)).displayOrder(1).build());
        when(transactionRepository.sumBlockSpent(eq(10L), any(), any())).thenReturn(new BlockSpent(12_300L, 12_300L));
        List<Transaction> recent = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            recent.add(reflected(living, today.atTime(9, i), 1_000, "편의점"));
        }
        when(transactionRepository.findTop12ByBlock_IdOrderByOccurredAtDescIdDesc(10L)).thenReturn(recent);

        BlockDetailResponse.SpendingDetail detail =
                (BlockDetailResponse.SpendingDetail) service.getDetail(USER_ID, 10L).detail();

        // 오늘이 기간 마지막 날(남은 1일)이라 아침 잔액 100,000원이 오늘 가능 금액이다
        assertThat(detail.today()).isEqualTo(new BlockDetailResponse.Today(100_000L, 12_300L, 87_700L));
        assertThat(detail.recentTransactions()).hasSize(5);
    }

    @Test
    @DisplayName("고정지출형: 납부 기록과 제때 낸 연속 개월 수를 채운다 (늦게 낸 달에서 멈춘다)")
    void fixedPayments() {
        Block rent = given(Block.builder().user(user).type(BlockType.FIXED).name("월세").balance(600_000L)
                .isDefault(false).startDate(LocalDate.of(2026, 1, 1))
                .rule(new BlockRule(null, null, null, 25, 600_000L, null, null)).displayOrder(2).build());
        when(transactionRepository.findTop12ByBlock_IdOrderByOccurredAtDescIdDesc(10L)).thenReturn(List.of(
                reflected(rent, LocalDateTime.of(2026, 10, 24, 9, 0), 600_000, "김*수"),
                reflected(rent, LocalDateTime.of(2026, 9, 25, 9, 0), 600_000, "김*수"),
                reflected(rent, LocalDateTime.of(2026, 8, 27, 9, 0), 600_000, "김*수"),
                reflected(rent, LocalDateTime.of(2026, 7, 25, 9, 0), 600_000, "김*수")));

        BlockDetailResponse.FixedDetail detail =
                (BlockDetailResponse.FixedDetail) service.getDetail(USER_ID, 10L).detail();

        assertThat(detail.onTimeStreak()).isEqualTo(2);
        assertThat(detail.recentPayments()).hasSize(4);
        assertThat(detail.recentPayments().getFirst()).isEqualTo(new BlockDetailResponse.Payment(
                LocalDate.of(2026, 10, 24), "김*수", "국민은행 ••1234", 600_000L, true));
        assertThat(detail.recentPayments().get(2).onTime()).isFalse();
    }

    @Test
    @DisplayName("고정지출형 블록 기록은 반영된 출금을 납부 기록으로 보여 준다")
    void fixedHistory() {
        Block rent = Block.builder().user(user).type(BlockType.FIXED).name("월세").balance(0L).isDefault(false)
                .rule(new BlockRule(null, null, null, 25, 600_000L, null, null)).displayOrder(2).build();
        ReflectionTestUtils.setField(rent, "id", 10L);
        when(blockRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(rent));
        when(transactionRepository.findByBlock_IdOrderByOccurredAtDescIdDesc(eq(10L), any()))
                .thenReturn(new SliceImpl<>(List.of(reflected(rent, LocalDateTime.of(2026, 10, 25, 9, 0), 600_000, "김*수")),
                        PageRequest.of(0, 20), true));

        BlockHistoryResponse history = service.getHistory(USER_ID, 10L, 0, 20);

        assertThat(history.items()).containsExactly(new BlockHistoryResponse.Item(LocalDate.of(2026, 10, 25),
                BlockHistoryResponse.Kind.PAYMENT, "김*수 납부", "국민은행 ••1234", -600_000L));
        assertThat(history.hasNext()).isTrue();
    }

    @Test
    @DisplayName("납부 기록이 없거나 최근 달이 빠지면 연속 횟수는 0이거나 끊긴다")
    void streakEdges() {
        assertThat(BlockDetailService.onTimeStreak(List.of())).isZero();
        assertThat(BlockDetailService.onTimeStreak(List.of(
                new BlockDetailResponse.Payment(LocalDate.of(2026, 10, 25), "김*수", null, 1, true),
                new BlockDetailResponse.Payment(LocalDate.of(2026, 8, 25), "김*수", null, 1, true)))).isEqualTo(1);
    }
}
