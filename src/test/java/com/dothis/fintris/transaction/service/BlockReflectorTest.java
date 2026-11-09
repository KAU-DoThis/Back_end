package com.dothis.fintris.transaction.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockCategoryRepository;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlockReflectorTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 11, 1, 12, 0);

    @Mock BlockRepository blockRepository;
    @Mock BlockCategoryRepository blockCategoryRepository;
    @Mock UserSettingRepository userSettingRepository;

    private BlockReflector reflector;
    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
    private Block living;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(user, "id", 1L);
        reflector = new BlockReflector(blockRepository, blockCategoryRepository, userSettingRepository,
                new BlockTargetResolver());
        living = Block.builder().user(user).type(BlockType.SPENDING).name("생활비").balance(30_000L).isDefault(true)
                .startDate(LocalDate.of(2026, 11, 1)).displayOrder(1).build();
        ReflectionTestUtils.setField(living, "id", 1L);
        when(blockRepository.findAllByUser_IdAndDeletedAtIsNullOrderByDisplayOrderAsc(anyLong())).thenReturn(List.of(living));
        when(blockCategoryRepository.findAllByUser_IdAndCategory_IdIn(anyLong(), any())).thenReturn(List.of());
    }

    private Transaction out(LocalDateTime at, long amount) {
        return Transaction.builder().user(user).occurredAt(at).amount(amount).direction(TransactionDirection.OUT)
                .merchantOrPayee("편의점").source(TransactionSource.CODEF).status(TransactionStatus.CONFIRMED).build();
    }

    @Test
    @DisplayName("반영하면 블록 남은 돈에서 빼고, 거래에 블록과 뺀 금액을 남긴다")
    void reflects() {
        Transaction transaction = out(CONFIRMED_AT.plusDays(1), 4_500);

        assertThat(reflector.reconcile(transaction, CONFIRMED_AT)).isTrue();

        assertThat(living.getBalance()).isEqualTo(25_500L);
        assertThat(transaction.getBlock()).isSameAs(living);
        assertThat(transaction.getBlockAmount()).isEqualTo(4_500L);
    }

    @Test
    @DisplayName("남은 돈보다 큰 출금은 0에서 멈추고 실제로 뺀 금액만 남긴다")
    void stopsAtZero() {
        Transaction transaction = out(CONFIRMED_AT.plusDays(1), 50_000);

        reflector.reconcile(transaction, CONFIRMED_AT);

        assertThat(living.getBalance()).isZero();
        assertThat(transaction.getBlockAmount()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("취소되면 뺀 금액만 블록에 돌려주고 반영을 푼다")
    void releasesOnCancel() {
        Transaction transaction = out(CONFIRMED_AT.plusDays(1), 50_000);
        reflector.reconcile(transaction, CONFIRMED_AT);

        transaction.cancel();
        assertThat(reflector.reconcile(transaction, CONFIRMED_AT)).isTrue();

        assertThat(living.getBalance()).isEqualTo(30_000L);
        assertThat(transaction.getBlock()).isNull();
        assertThat(transaction.getBlockAmount()).isNull();
    }

    @Test
    @DisplayName("부분 취소로 금액이 줄면 되돌린 뒤 줄어든 금액으로 다시 뺀다")
    void reReflectsOnAmountChange() {
        Transaction transaction = out(CONFIRMED_AT.plusDays(1), 10_000);
        reflector.reconcile(transaction, CONFIRMED_AT);

        transaction.changeAmount(7_000);
        reflector.reconcile(transaction, CONFIRMED_AT);

        assertThat(living.getBalance()).isEqualTo(23_000L);
        assertThat(transaction.getBlockAmount()).isEqualTo(7_000L);
    }

    @Test
    @DisplayName("이미 맞게 반영된 거래를 다시 맞추면 바뀐 게 없다")
    void noChange() {
        Transaction transaction = out(CONFIRMED_AT.plusDays(1), 4_500);
        reflector.reconcile(transaction, CONFIRMED_AT);

        assertThat(reflector.reconcile(transaction, CONFIRMED_AT)).isFalse();
        assertThat(living.getBalance()).isEqualTo(25_500L);
    }

    @Test
    @DisplayName("블록 확정 전 거래, 블록 확정을 안 한 사용자, 입금, 소비 제외 거래는 반영하지 않는다")
    void skipsNonTargets() {
        Transaction before = out(CONFIRMED_AT.minusMinutes(1), 4_500);
        Transaction excluded = out(CONFIRMED_AT.plusDays(1), 4_500);
        excluded.excludeFromSpending();
        Transaction income = Transaction.builder().user(user).occurredAt(CONFIRMED_AT.plusDays(1)).amount(100_000)
                .direction(TransactionDirection.IN).source(TransactionSource.CODEF)
                .status(TransactionStatus.CONFIRMED).build();

        assertThat(reflector.reconcile(before, CONFIRMED_AT)).isFalse();
        assertThat(reflector.reconcile(out(CONFIRMED_AT.plusDays(1), 4_500), null)).isFalse();
        assertThat(reflector.reconcile(income, CONFIRMED_AT)).isFalse();
        assertThat(reflector.reconcile(excluded, CONFIRMED_AT)).isFalse();
        assertThat(living.getBalance()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("블록이 삭제된 뒤 되돌리면 그 블록에 돈을 넣지 않는다 (삭제 때 이미 나눌 돈으로 돌아감)")
    void doesNotDepositToDeletedBlock() {
        Transaction transaction = out(CONFIRMED_AT.plusDays(1), 4_500);
        reflector.reconcile(transaction, CONFIRMED_AT);
        living.delete(CONFIRMED_AT.plusDays(2));
        when(blockRepository.findAllByUser_IdAndDeletedAtIsNullOrderByDisplayOrderAsc(anyLong())).thenReturn(List.of());

        transaction.cancel();
        reflector.reconcile(transaction, CONFIRMED_AT);

        assertThat(living.getBalance()).isZero();
        assertThat(transaction.getBlock()).isNull();
    }
}
