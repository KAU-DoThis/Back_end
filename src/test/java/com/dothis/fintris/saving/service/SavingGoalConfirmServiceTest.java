package com.dothis.fintris.saving.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.entity.TransferReason;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.saving.entity.SavingGoal;
import com.dothis.fintris.saving.entity.SavingGoalStatus;
import com.dothis.fintris.saving.repository.SavingGoalRepository;
import com.dothis.fintris.saving.service.SavingGoalConfirmService.DueGroup;
import com.dothis.fintris.transaction.dto.SyncStatus;
import com.dothis.fintris.transaction.service.SyncStateRegistry;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.waitingfund.entity.WaitingFund;
import com.dothis.fintris.waitingfund.entity.WaitingFundSource;
import com.dothis.fintris.waitingfund.repository.WaitingFundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavingGoalConfirmServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate START = LocalDate.of(2026, 10, 15);
    private static final LocalDate END = LocalDate.of(2026, 11, 14);

    @Mock SavingGoalRepository savingGoalRepository;
    @Mock SavingGoalService savingGoalService;
    @Mock BlockRepository blockRepository;
    @Mock BlockTransferRepository blockTransferRepository;
    @Mock WaitingFundRepository waitingFundRepository;
    @Mock LinkedAccountRepository linkedAccountRepository;
    @Mock SyncStateRegistry syncStateRegistry;
    @InjectMocks SavingGoalConfirmService service;

    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
    private Block living;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        living = Block.builder().user(user).type(BlockType.SPENDING).name("생활비").balance(500_000)
                .isDefault(true).startDate(START).displayOrder(0).build();
        lenient().when(blockRepository.findFirstByUser_IdAndIsDefaultTrueAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(living));
        lenient().when(waitingFundRepository.save(any(WaitingFund.class))).thenAnswer(inv -> {
            WaitingFund fund = inv.getArgument(0);
            ReflectionTestUtils.setField(fund, "id", 12L);
            return fund;
        });
    }

    private SavingGoal goal(Long id, Long categoryId, String name, long base, long actual, LocalDate end) {
        Category category = Category.builder().code(name).name(name).categoryType(CategoryType.EXPENSE)
                .displayOrder(1).build();
        ReflectionTestUtils.setField(category, "id", categoryId);
        SavingGoal goal = SavingGoal.builder().user(user).category(category).baseAmount(base).targetAmount(base / 2)
                .startDate(START).endDate(end).activeKey(USER_ID + ":" + categoryId).build();
        ReflectionTestUtils.setField(goal, "id", id);
        lenient().when(savingGoalService.spent(USER_ID, categoryId, START, end.plusDays(1))).thenReturn(actual);
        return goal;
    }

    private SavingGoal goal(Long id, Long categoryId, String name, long base, long actual) {
        return goal(id, categoryId, name, base, actual, END);
    }

    private DueGroup group(Long... ids) {
        return new DueGroup(USER_ID, END, List.of(ids));
    }

    @Test
    @DisplayName("확정 절약액을 생활비에서 꺼내 절약분 하나로 묶고 카테고리별 내역을 남긴다")
    void confirm() {
        SavingGoal delivery = goal(9L, 2L, "배달", 132_000, 80_000); // 52,000 절약
        SavingGoal cafe = goal(10L, 3L, "카페", 72_000, 37_000);     // 35,000 절약
        when(savingGoalRepository.findAllById(List.of(9L, 10L))).thenReturn(List.of(delivery, cafe));

        service.confirm(group(9L, 10L));

        assertThat(living.getBalance()).isEqualTo(500_000 - 87_000);
        ArgumentCaptor<WaitingFund> fund = ArgumentCaptor.forClass(WaitingFund.class);
        verify(waitingFundRepository).save(fund.capture());
        assertThat(fund.getValue().getSource()).isEqualTo(WaitingFundSource.SAVING);
        assertThat(fund.getValue().getAmount()).isEqualTo(87_000);
        assertThat(fund.getValue().getDescription()).isEqualTo("11월 절약 확정");
        assertThat(fund.getValue().getBreakdown()).containsExactly(
                new WaitingFund.Breakdown("배달", 52_000), new WaitingFund.Breakdown("카페", 35_000));
        ArgumentCaptor<BlockTransfer> transfer = ArgumentCaptor.forClass(BlockTransfer.class);
        verify(blockTransferRepository).save(transfer.capture());
        assertThat(transfer.getValue().getReason()).isEqualTo(TransferReason.SAVING);
        assertThat(transfer.getValue().getAmount()).isEqualTo(87_000);

        assertThat(delivery.getStatus()).isEqualTo(SavingGoalStatus.CONFIRMED);
        assertThat(delivery.getActualAmount()).isEqualTo(80_000);
        assertThat(delivery.getConfirmedSaving()).isEqualTo(52_000);
        assertThat(delivery.getWaitingFundId()).isEqualTo(12L);
        assertThat(delivery.getActiveKey()).isNull();
    }

    @Test
    @DisplayName("생활비가 모자라면 꺼낼 수 있는 만큼만 옮기고, 카테고리별 내역도 같은 비율로 줄여 합을 맞춘다")
    void capByLivingBalance() {
        ReflectionTestUtils.setField(living, "balance", 30_000L);
        SavingGoal delivery = goal(9L, 2L, "배달", 132_000, 80_000); // 52,000
        SavingGoal cafe = goal(10L, 3L, "카페", 72_000, 37_000);     // 35,000
        when(savingGoalRepository.findAllById(List.of(9L, 10L))).thenReturn(List.of(delivery, cafe));

        service.confirm(group(9L, 10L));

        assertThat(living.getBalance()).isZero();
        ArgumentCaptor<WaitingFund> fund = ArgumentCaptor.forClass(WaitingFund.class);
        verify(waitingFundRepository).save(fund.capture());
        assertThat(fund.getValue().getAmount()).isEqualTo(30_000);
        // 52,000 × 30,000 ÷ 87,000 = 17,931 (+남는 1원) / 35,000 × 30,000 ÷ 87,000 = 12,068
        assertThat(fund.getValue().getBreakdown()).containsExactly(
                new WaitingFund.Breakdown("배달", 17_932), new WaitingFund.Breakdown("카페", 12_068));
        assertThat(fund.getValue().getBreakdown().stream().mapToLong(WaitingFund.Breakdown::amount).sum())
                .isEqualTo(30_000);
        // 목표의 확정 절약액은 계산값 그대로 남긴다
        assertThat(delivery.getConfirmedSaving()).isEqualTo(52_000);
    }

    @Test
    @DisplayName("기준보다 많이 썼으면 절약액 0으로 확정하고 나눌 돈은 만들지 않는다")
    void noSaving() {
        SavingGoal delivery = goal(9L, 2L, "배달", 132_000, 150_000);
        when(savingGoalRepository.findAllById(List.of(9L))).thenReturn(List.of(delivery));

        service.confirm(group(9L));

        assertThat(living.getBalance()).isEqualTo(500_000);
        verify(waitingFundRepository, never()).save(any());
        assertThat(delivery.getStatus()).isEqualTo(SavingGoalStatus.CONFIRMED);
        assertThat(delivery.getConfirmedSaving()).isZero();
        assertThat(delivery.getWaitingFundId()).isNull();
    }

    @Test
    @DisplayName("다시 읽었을 때 이미 확정·취소된 목표는 건너뛴다")
    void skipsNotActive() {
        SavingGoal canceled = goal(9L, 2L, "배달", 132_000, 80_000);
        canceled.cancel();
        when(savingGoalRepository.findAllById(List.of(9L))).thenReturn(List.of(canceled));

        service.confirm(group(9L));

        verify(waitingFundRepository, never()).save(any());
        assertThat(living.getBalance()).isEqualTo(500_000);
        assertThat(canceled.getStatus()).isEqualTo(SavingGoalStatus.CANCELED);
    }

    @Test
    @DisplayName("확정 대상은 사용자·종료일별로 묶는다")
    void groupsByEndDate() {
        SavingGoal oct = goal(9L, 2L, "배달", 132_000, 80_000, LocalDate.of(2026, 10, 31));
        SavingGoal nov1 = goal(10L, 3L, "카페", 72_000, 37_000, LocalDate.of(2026, 11, 1));
        SavingGoal nov1b = goal(11L, 4L, "택시", 84_000, 40_000, LocalDate.of(2026, 11, 1));
        when(savingGoalRepository.findAllByStatusAndEndDateBefore(SavingGoalStatus.ACTIVE, LocalDate.of(2026, 11, 2)))
                .thenReturn(List.of(oct, nov1, nov1b));

        assertThat(service.dueGroups(LocalDate.of(2026, 11, 2))).containsExactly(
                new DueGroup(USER_ID, LocalDate.of(2026, 10, 31), List.of(9L)),
                new DueGroup(USER_ID, LocalDate.of(2026, 11, 1), List.of(10L, 11L)));
    }

    private LinkedAccount account(LocalDate syncedOn) {
        LinkedAccount account = LinkedAccount.builder().user(user).institutionCode("0004").connectedId("x").build();
        ReflectionTestUtils.setField(account, "lastSyncedAt", syncedOn == null ? null : syncedOn.atTime(6, 0));
        return account;
    }

    @Test
    @DisplayName("기간이 끝난 뒤 모든 계좌 동기화가 끝났을 때만 확정한다")
    void syncedAfter() {
        when(syncStateRegistry.get(USER_ID)).thenReturn(new SyncStateRegistry.SyncState(SyncStatus.DONE, 0));
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(account(END.plusDays(1)), account(END.plusDays(1))));
        assertThat(service.isSyncedAfter(USER_ID, END)).isTrue();

        // 한 계좌라도 종료일 다음 날 동기화를 못 했으면(실패 포함) 기다린다
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(account(END.plusDays(1)), account(END)));
        assertThat(service.isSyncedAfter(USER_ID, END)).isFalse();

        // 동기화가 진행 중이면 기다린다
        when(syncStateRegistry.get(USER_ID)).thenReturn(new SyncStateRegistry.SyncState(SyncStatus.IN_PROGRESS, 0));
        assertThat(service.isSyncedAfter(USER_ID, END)).isFalse();
    }
}
