package com.dothis.fintris.saving.service;

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
import static org.mockito.ArgumentMatchers.eq;
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

    private SavingGoal goal(Long id, Long categoryId, String name, long base, long actual) {
        Category category = Category.builder().code(name).name(name).categoryType(CategoryType.EXPENSE)
                .displayOrder(1).build();
        ReflectionTestUtils.setField(category, "id", categoryId);
        SavingGoal goal = SavingGoal.builder().user(user).category(category).baseAmount(base).targetAmount(base / 2)
                .startDate(START).endDate(END).build();
        ReflectionTestUtils.setField(goal, "id", id);
        when(savingGoalService.spent(USER_ID, categoryId, START, END.plusDays(1)))
                .thenReturn(new SpentStats(actual, 5));
        return goal;
    }

    @Test
    @DisplayName("확정 절약액을 생활비에서 꺼내 절약분 하나로 묶고 카테고리별 내역을 남긴다")
    void confirm() {
        SavingGoal delivery = goal(9L, 2L, "배달", 132_000, 80_000); // 52,000 절약
        SavingGoal cafe = goal(10L, 3L, "카페", 72_000, 37_000);     // 35,000 절약
        when(savingGoalRepository.findAllById(List.of(9L, 10L))).thenReturn(List.of(delivery, cafe));

        service.confirmUser(USER_ID, List.of(9L, 10L));

        assertThat(living.getBalance()).isEqualTo(500_000 - 87_000);
        ArgumentCaptor<WaitingFund> fund = ArgumentCaptor.forClass(WaitingFund.class);
        verify(waitingFundRepository).save(fund.capture());
        assertThat(fund.getValue().getSource()).isEqualTo(WaitingFundSource.SAVING);
        assertThat(fund.getValue().getAmount()).isEqualTo(87_000);
        assertThat(fund.getValue().getBreakdown()).containsExactly(
                new WaitingFund.Breakdown("배달", 52_000), new WaitingFund.Breakdown("카페", 35_000));
        ArgumentCaptor<BlockTransfer> transfer = ArgumentCaptor.forClass(BlockTransfer.class);
        verify(blockTransferRepository).save(transfer.capture());
        assertThat(transfer.getValue().getReason()).isEqualTo(TransferReason.SAVING);
        assertThat(transfer.getValue().getAmount()).isEqualTo(87_000);

        assertThat(delivery.getStatus()).isEqualTo(SavingGoalStatus.CONFIRMED);
        assertThat(delivery.getActualAmount()).isEqualTo(80_000);
        assertThat(delivery.getActualCount()).isEqualTo(5);
        assertThat(delivery.getConfirmedSaving()).isEqualTo(52_000);
        assertThat(delivery.getWaitingFundId()).isEqualTo(12L);
    }

    @Test
    @DisplayName("생활비가 모자라면 꺼낼 수 있는 만큼만 옮긴다 (블록 합 + 나눌 돈 합 유지)")
    void capByLivingBalance() {
        ReflectionTestUtils.setField(living, "balance", 30_000L);
        SavingGoal delivery = goal(9L, 2L, "배달", 132_000, 80_000);
        when(savingGoalRepository.findAllById(List.of(9L))).thenReturn(List.of(delivery));

        service.confirmUser(USER_ID, List.of(9L));

        assertThat(living.getBalance()).isZero();
        ArgumentCaptor<WaitingFund> fund = ArgumentCaptor.forClass(WaitingFund.class);
        verify(waitingFundRepository).save(fund.capture());
        assertThat(fund.getValue().getAmount()).isEqualTo(30_000);
        assertThat(fund.getValue().getSavingGoalId()).isEqualTo(9L);
        assertThat(delivery.getConfirmedSaving()).isEqualTo(52_000);
    }

    @Test
    @DisplayName("기준보다 많이 썼으면 절약액 0으로 확정하고 나눌 돈은 만들지 않는다")
    void noSaving() {
        SavingGoal delivery = goal(9L, 2L, "배달", 132_000, 150_000);
        when(savingGoalRepository.findAllById(List.of(9L))).thenReturn(List.of(delivery));

        service.confirmUser(USER_ID, List.of(9L));

        assertThat(living.getBalance()).isEqualTo(500_000);
        verify(waitingFundRepository, never()).save(any());
        assertThat(delivery.getStatus()).isEqualTo(SavingGoalStatus.CONFIRMED);
        assertThat(delivery.getConfirmedSaving()).isZero();
        assertThat(delivery.getWaitingFundId()).isNull();
    }

    @Test
    @DisplayName("이미 확정·취소된 목표는 다시 확정하지 않는다")
    void skipsNotActive() {
        SavingGoal canceled = SavingGoal.builder().user(user).baseAmount(1).targetAmount(0)
                .startDate(START).endDate(END).status(SavingGoalStatus.CANCELED).build();
        when(savingGoalRepository.findAllById(List.of(9L))).thenReturn(List.of(canceled));

        service.confirmUser(USER_ID, List.of(9L));

        verify(savingGoalService, never()).spent(any(), any(), any(), any());
        verify(waitingFundRepository, never()).save(any());
        assertThat(living.getBalance()).isEqualTo(500_000);
        verify(blockRepository, never()).findFirstByUser_IdAndIsDefaultTrueAndDeletedAtIsNull(eq(USER_ID));
    }
}
