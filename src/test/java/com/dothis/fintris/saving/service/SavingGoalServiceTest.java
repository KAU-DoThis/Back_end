package com.dothis.fintris.saving.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.saving.code.SavingGoalErrorCode;
import com.dothis.fintris.saving.dto.SavingGoalCreateRequest;
import com.dothis.fintris.saving.dto.SavingGoalResponse;
import com.dothis.fintris.saving.entity.SavingGoal;
import com.dothis.fintris.saving.entity.SavingGoalStatus;
import com.dothis.fintris.saving.repository.SavingGoalRepository;
import com.dothis.fintris.transaction.repository.CategorySpent;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.repository.UserRepository;
import com.dothis.fintris.waitingfund.entity.WaitingFund;
import com.dothis.fintris.waitingfund.entity.WaitingFundSource;
import com.dothis.fintris.waitingfund.repository.WaitingFundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavingGoalServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Mock SavingGoalRepository savingGoalRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock BlockRepository blockRepository;
    @Mock UserRepository userRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock WaitingFundRepository waitingFundRepository;
    @InjectMocks SavingGoalService service;

    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
    private final Category taxi = Category.builder().code("TRANSPORT").name("택시")
            .categoryType(CategoryType.EXPENSE).displayOrder(1).build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(taxi, "id", 4L);
        lenient().when(categoryRepository.findById(4L)).thenReturn(Optional.of(taxi));
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(savingGoalRepository.save(any(SavingGoal.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // 지난달 택시 6회 84,000원(1회 평균 14,000), 기간 중에는 0원
    private void spentLastMonth() {
        LocalDateTime baseFrom = SavingGoalCalculator.baseFrom(TODAY).atStartOfDay();
        lenient().when(transactionRepository.sumSpentByCategory(eq(USER_ID), eq(baseFrom), any()))
                .thenReturn(List.of(new CategorySpent(4L, "TRANSPORT", "택시", 6L, 84_000L),
                        new CategorySpent(2L, "DELIVERY", "배달", 9L, 300_000L)));
    }

    private SavingGoalCreateRequest request(long targetAmount, Long linkedBlockId) {
        return new SavingGoalCreateRequest(4L, null, targetAmount, null, null, linkedBlockId, 21L);
    }

    private SavingGoalCreateRequest countRequest(int targetCount) {
        return new SavingGoalCreateRequest(4L, targetCount, null, null, null, null, 21L);
    }

    private void assertError(Runnable call, SavingGoalErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(code);
    }

    @Test
    @DisplayName("생성: 기준은 서버가 지난달 지출·횟수로 계산하고 기간은 기본 1개월")
    void create() {
        spentLastMonth();
        Block travel = Block.builder().user(user).type(BlockType.GOAL).name("여행").balance(0)
                .targetAmount(800_000L).startDate(TODAY).displayOrder(1).build();
        ReflectionTestUtils.setField(travel, "id", 7L);
        when(blockRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(travel));

        SavingGoalResponse response = service.create(USER_ID, request(42_000, 7L));

        assertThat(response.baseAmount()).isEqualTo(84_000);
        assertThat(response.baseCount()).isEqualTo(6);
        assertThat(response.averageAmount()).isEqualTo(14_000);
        assertThat(response.targetCount()).isNull();
        assertThat(response.expectedSaving()).isEqualTo(42_000);
        assertThat(response.startDate()).isEqualTo(TODAY);
        assertThat(response.endDate()).isEqualTo(TODAY.plusMonths(1).minusDays(1));
        assertThat(response.status()).isEqualTo(SavingGoalStatus.ACTIVE);
        assertThat(response.linkedBlock().name()).isEqualTo("여행");
        assertThat(response.suggestionId()).isEqualTo(21L);
    }

    @Test
    @DisplayName("생성: 횟수로 정하면 목표 금액 = 지난달 지출 × 목표 횟수 ÷ 지난달 횟수")
    void createByCount() {
        spentLastMonth();

        SavingGoalResponse response = service.create(USER_ID, countRequest(4));

        assertThat(response.targetCount()).isEqualTo(4);
        assertThat(response.targetAmount()).isEqualTo(56_000);
        assertThat(response.expectedSaving()).isEqualTo(28_000);
        assertThat(response.spentCount()).isZero();
    }

    @Test
    @DisplayName("생성: 목표가 지난달 이상이면(금액·횟수 모두) 예상 절약액 없음으로 막는다")
    void noExpectedSaving() {
        spentLastMonth();

        assertError(() -> service.create(USER_ID, request(84_000, null)),
                SavingGoalErrorCode.SAVING_GOAL_NO_EXPECTED_SAVING);
        assertError(() -> service.create(USER_ID, countRequest(6)), SavingGoalErrorCode.SAVING_GOAL_NO_EXPECTED_SAVING);
        verify(savingGoalRepository, never()).save(any());
    }

    @Test
    @DisplayName("생성: 목표 없음·음수·거꾸로 된 기간·같은 카테고리 진행 중·목표형이 아닌 블록은 막는다")
    void rejects() {
        spentLastMonth();
        assertError(() -> service.create(USER_ID, request(-1, null)), SavingGoalErrorCode.SAVING_GOAL_INVALID_AMOUNT);
        assertError(() -> service.create(USER_ID, countRequest(-1)), SavingGoalErrorCode.SAVING_GOAL_INVALID_AMOUNT);
        assertError(() -> service.create(USER_ID, new SavingGoalCreateRequest(4L, null, null, null, null, null, null)),
                SavingGoalErrorCode.SAVING_GOAL_INVALID_AMOUNT);
        assertError(() -> service.create(USER_ID, new SavingGoalCreateRequest(4L, null, 42_000L, TODAY.plusDays(5),
                TODAY.plusDays(1), null, null)), SavingGoalErrorCode.SAVING_GOAL_INVALID_PERIOD);

        Block living = Block.builder().user(user).type(BlockType.SPENDING).name("생활비").balance(0)
                .startDate(TODAY).displayOrder(0).build();
        when(blockRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(living));
        assertError(() -> service.create(USER_ID, request(42_000, 1L)),
                SavingGoalErrorCode.SAVING_GOAL_INVALID_LINKED_BLOCK);

        when(savingGoalRepository.existsByUser_IdAndCategory_IdAndStatus(USER_ID, 4L, SavingGoalStatus.ACTIVE))
                .thenReturn(true);
        assertError(() -> service.create(USER_ID, request(42_000, null)),
                SavingGoalErrorCode.SAVING_GOAL_DUPLICATE_CATEGORY);
    }

    private SavingGoal saved(Long id, SavingGoalStatus status) {
        SavingGoal goal = SavingGoal.builder().user(user).category(taxi).baseAmount(84_000).targetAmount(42_000)
                .startDate(TODAY.minusDays(40)).endDate(TODAY.minusDays(10)).status(status).build();
        ReflectionTestUtils.setField(goal, "id", id);
        lenient().when(savingGoalRepository.findById(id)).thenReturn(Optional.of(goal));
        return goal;
    }

    @Test
    @DisplayName("확정 뒤 절약분을 다 나눴으면 ALLOCATED로 보여 준다")
    void allocated() {
        SavingGoal goal = saved(9L, SavingGoalStatus.ACTIVE);
        goal.confirm(60_000, 5, 24_000, 12L, LocalDateTime.now());
        WaitingFund fund = WaitingFund.builder().user(user).source(WaitingFundSource.SAVING).label("절약분")
                .amount(0).originalAmount(24_000).build();
        ReflectionTestUtils.setField(fund, "id", 12L);
        when(waitingFundRepository.findById(12L)).thenReturn(Optional.of(fund));

        SavingGoalResponse response = service.getGoal(USER_ID, 9L);

        assertThat(response.status()).isEqualTo(SavingGoalStatus.ALLOCATED);
        assertThat(response.spent()).isEqualTo(60_000);
        assertThat(response.spentCount()).isEqualTo(5);
        assertThat(response.confirmedSaving()).isEqualTo(24_000);
    }

    @Test
    @DisplayName("취소: 진행 중인 목표만 취소하고, 취소한 목표는 없는 것으로 본다")
    void cancel() {
        SavingGoal active = saved(9L, SavingGoalStatus.ACTIVE);
        saved(10L, SavingGoalStatus.CONFIRMED);

        service.cancel(USER_ID, 9L);

        assertThat(active.getStatus()).isEqualTo(SavingGoalStatus.CANCELED);
        assertError(() -> service.cancel(USER_ID, 9L), SavingGoalErrorCode.SAVING_GOAL_NOT_FOUND);
        assertError(() -> service.cancel(USER_ID, 10L), SavingGoalErrorCode.SAVING_GOAL_NOT_ACTIVE);
        assertError(() -> service.getGoal(2L, 10L), SavingGoalErrorCode.SAVING_GOAL_FORBIDDEN);
    }
}
