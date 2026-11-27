package com.dothis.fintris.report.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.report.code.ReportErrorCode;
import com.dothis.fintris.report.dto.MonthlyReportResponse;
import com.dothis.fintris.saving.entity.SavingGoal;
import com.dothis.fintris.saving.entity.SavingGoalStatus;
import com.dothis.fintris.saving.repository.SavingGoalRepository;
import com.dothis.fintris.transaction.repository.BlockSpent;
import com.dothis.fintris.transaction.repository.CategorySpent;
import com.dothis.fintris.transaction.repository.DayTotal;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.user.entity.UserSetting;
import com.dothis.fintris.user.repository.UserSettingRepository;
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
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final YearMonth SEP = YearMonth.of(2026, 9);
    private static final LocalDateTime SEP_1 = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime OCT_1 = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final LocalDateTime AUG_1 = LocalDateTime.of(2026, 8, 1, 0, 0);

    @Mock TransactionRepository transactionRepository;
    @Mock BlockRepository blockRepository;
    @Mock BlockTransferRepository blockTransferRepository;
    @Mock SavingGoalRepository savingGoalRepository;
    @Mock UserSettingRepository userSettingRepository;
    @InjectMocks MonthlyReportService service;

    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        lenient().when(transactionRepository.sumByDay(eq(USER_ID), any(), any(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());
        lenient().when(transactionRepository.sumSpentByCategory(eq(USER_ID), any(), any())).thenReturn(List.of());
        lenient().when(savingGoalRepository.findAllByUser_IdAndStatusAndEndDateBetween(eq(USER_ID),
                eq(SavingGoalStatus.CONFIRMED), any(), any())).thenReturn(List.of());
        lenient().when(blockRepository.findAllByUser_IdOrderByDisplayOrderAsc(USER_ID))
                .thenReturn(List.of());
        usingSince(LocalDate.of(2026, 7, 1));
    }

    private void usingSince(LocalDate date) {
        UserSetting setting = UserSetting.builder().blocksConfirmed(date != null).build();
        if (date != null) {
            setting.moveBlocksConfirmedAt(date.atTime(10, 0));
        }
        lenient().when(userSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(setting));
    }

    private Block block(Long id, BlockType type, String name, BlockRule rule, Long target) {
        Block block = Block.builder().user(user).type(type).name(name).balance(0).isDefault(type == BlockType.SPENDING)
                .targetAmount(target).startDate(LocalDate.of(2026, 7, 1)).dueDate(LocalDate.of(2027, 6, 30))
                .rule(rule).displayOrder(id.intValue()).build();
        ReflectionTestUtils.setField(block, "id", id);
        return block;
    }

    @Test
    @DisplayName("총 지출은 지난달 대비 금액·%와 날짜별 누적으로, 9월에 없는 31일은 null")
    void spending() {
        when(transactionRepository.sumByDay(eq(USER_ID), eq(SEP_1), eq(OCT_1), isNull(), isNull(), isNull()))
                .thenReturn(List.of(new DayTotal(LocalDate.of(2026, 9, 1), 1_000_000L),
                        new DayTotal(LocalDate.of(2026, 9, 30), 1_140_000L)));
        when(transactionRepository.sumByDay(eq(USER_ID), eq(AUG_1), eq(SEP_1), isNull(), isNull(), isNull()))
                .thenReturn(List.of(new DayTotal(LocalDate.of(2026, 8, 31), 2_230_000L)));

        MonthlyReportResponse.Spending spending = service.getMonthly(USER_ID, SEP).spending();

        assertThat(spending.total()).isEqualTo(2_140_000);
        assertThat(spending.previousTotal()).isEqualTo(2_230_000);
        assertThat(spending.changeAmount()).isEqualTo(-90_000);
        assertThat(spending.changeRate()).isEqualTo(-4);
        assertThat(spending.daily()).hasSize(31);
        assertThat(spending.daily().get(0)).isEqualTo(new MonthlyReportResponse.DailyPoint(1, 1_000_000L, 0L));
        assertThat(spending.daily().get(29).cumulative()).isEqualTo(2_140_000);
        assertThat(spending.daily().get(30)).isEqualTo(new MonthlyReportResponse.DailyPoint(31, null, 2_230_000L));
    }

    @Test
    @DisplayName("주요 소비 변화: 지난달 대비 차이가 큰 3개, 차이 없는 카테고리는 뺀다")
    void categoryChanges() {
        when(transactionRepository.sumSpentByCategory(USER_ID, AUG_1, SEP_1)).thenReturn(List.of(
                new CategorySpent(2L, "DELIVERY", "배달", 10L, 172_000L),
                new CategorySpent(3L, "CAFE", "카페", 20L, 101_000L),
                new CategorySpent(8L, "SHOPPING", "쇼핑", 3L, 88_000L),
                new CategorySpent(5L, "TELECOM", "통신", 1L, 55_000L)));
        when(transactionRepository.sumSpentByCategory(USER_ID, SEP_1, OCT_1)).thenReturn(List.of(
                new CategorySpent(2L, "DELIVERY", "배달", 7L, 120_000L),
                new CategorySpent(3L, "CAFE", "카페", 14L, 66_000L),
                new CategorySpent(8L, "SHOPPING", "쇼핑", 4L, 129_000L),
                new CategorySpent(5L, "TELECOM", "통신", 1L, 55_000L),
                new CategorySpent(null, null, null, 1L, 3_000L)));

        MonthlyReportResponse report = service.getMonthly(USER_ID, SEP);

        assertThat(report.categoryChanges()).extracting(MonthlyReportResponse.CategoryChange::name,
                        MonthlyReportResponse.CategoryChange::change)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("배달", -52_000L),
                        org.assertj.core.groups.Tuple.tuple("쇼핑", 41_000L),
                        org.assertj.core.groups.Tuple.tuple("카페", -35_000L));
        assertThat(report.comment().text()).contains("쇼핑 지출이 41,000원 늘었어요");
    }

    @Test
    @DisplayName("절약: 9월에 확정된 목표의 확정·예상 절약액 합과 절약분을 나눈 블록")
    void saving() {
        Category delivery = Category.builder().code("DELIVERY").name("배달").categoryType(CategoryType.EXPENSE)
                .displayOrder(1).build();
        SavingGoal goal = SavingGoal.builder().user(user).category(delivery).baseAmount(172_000).baseCount(10)
                .targetAmount(74_000).startDate(LocalDate.of(2026, 9, 1)).endDate(LocalDate.of(2026, 9, 30))
                .build(); // 예상 절약액 98,000
        goal.confirm(85_000, 7, 87_000, 12L, LocalDateTime.of(2026, 10, 1, 6, 30));
        when(savingGoalRepository.findAllByUser_IdAndStatusAndEndDateBetween(USER_ID, SavingGoalStatus.CONFIRMED,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(List.of(goal));
        Block travel = block(4L, BlockType.GOAL, "여행", null, 800_000L);
        Block saving = block(6L, BlockType.GOAL, "저축", null, 1_000_000L);
        when(blockTransferRepository.findAllocationsFrom(List.of(12L))).thenReturn(List.of(
                BlockTransfer.builder().toBlock(travel).amount(50_000).build(),
                BlockTransfer.builder().toBlock(saving).amount(37_000).build()));

        MonthlyReportResponse report = service.getMonthly(USER_ID, SEP);

        assertThat(report.saving().confirmed()).isEqualTo(87_000);
        assertThat(report.saving().expected()).isEqualTo(98_000);
        assertThat(report.saving().allocated()).isEqualTo(87_000);
        assertThat(report.saving().allocatedBlocks()).extracting(MonthlyReportResponse.AllocatedBlock::name)
                .containsExactly("여행", "저축");
        // 절약 실천 = 87,000 ÷ 98,000 = 89%
        assertThat(report.score().items()).filteredOn(i -> i.key().equals("SAVING"))
                .extracting(MonthlyReportResponse.ScoreItem::rate).containsExactly(89);
    }

    @Test
    @DisplayName("점수: 고정지출 납부·생활비 페이스·목표 확보를 계산하고 절약 목표가 없으면 그 배점을 나눈다")
    void score() {
        Block living = block(1L, BlockType.SPENDING, "생활비", new BlockRule(300_000L, 1, null, null, null, null, null), null);
        Block rent = block(2L, BlockType.FIXED, "월세", new BlockRule(null, null, null, 25, 600_000L, null, null), null);
        Block travel = block(4L, BlockType.GOAL, "여행", new BlockRule(null, null, 200_000L, null, null, null, null),
                800_000L);
        when(blockRepository.findAllByUser_IdOrderByDisplayOrderAsc(USER_ID))
                .thenReturn(List.of(living, rent, travel));
        // 월세: 9/25까지 600,000원 납부 → 100%
        when(transactionRepository.sumBlockSpent(2L, SEP_1, LocalDateTime.of(2026, 9, 26, 0, 0)))
                .thenReturn(new BlockSpent(600_000L, 600_000L));
        // 생활비: 매일 10,000원씩 → 30일 모두 안전 → 100%
        when(transactionRepository.sumBlockSpentByDay(1L, SEP_1, OCT_1)).thenReturn(
                java.util.stream.IntStream.rangeClosed(1, 30)
                        .mapToObj(d -> new DayTotal(LocalDate.of(2026, 9, d), 10_000L)).toList());
        // 여행: 9월에 100,000원 확보 / 월 200,000원 → 50%
        when(blockTransferRepository.sumInSince(4L, SEP_1)).thenReturn(150_000L);
        when(blockTransferRepository.sumOutSince(4L, SEP_1)).thenReturn(0L);
        when(blockTransferRepository.sumInSince(4L, OCT_1)).thenReturn(50_000L);
        when(blockTransferRepository.sumOutSince(4L, OCT_1)).thenReturn(0L);
        lenient().when(transactionRepository.sumBlockSpent(eq(2L), eq(AUG_1), any()))
                .thenReturn(new BlockSpent(0L, 0L));
        lenient().when(transactionRepository.sumBlockSpentByDay(1L, AUG_1, SEP_1)).thenReturn(List.of());
        lenient().when(blockTransferRepository.sumInSince(eq(4L), eq(AUG_1))).thenReturn(150_000L);

        MonthlyReportResponse.Score score = service.getMonthly(USER_ID, SEP).score();

        assertThat(score.items()).extracting(MonthlyReportResponse.ScoreItem::key, MonthlyReportResponse.ScoreItem::rate)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FIXED", 100),
                        org.assertj.core.groups.Tuple.tuple("PACE", 100),
                        org.assertj.core.groups.Tuple.tuple("SAVING", null),
                        org.assertj.core.groups.Tuple.tuple("GOAL", 50));
        // (100×30 + 100×30 + 50×25) ÷ 85 = 7,250 ÷ 85 = 85.3 → 85
        assertThat(score.total()).isEqualTo(85);
        assertThat(score.note()).isNull();
    }

    private Integer fixedRate() {
        return service.getMonthly(USER_ID, SEP).score().items().stream()
                .filter(i -> i.key().equals("FIXED")).findFirst().orElseThrow().rate();
    }

    @Test
    @DisplayName("고정지출: 허용 일수가 다음 달로 넘어가도 그 기간의 납부를 인정한다 (9/30 + 3일 → 10/3까지)")
    void fixedWindowIntoNextMonth() {
        BlockRule.MatchCondition match = new BlockRule.MatchCondition(null, null, null, null, 3, null);
        Block rent = block(2L, BlockType.FIXED, "월세", new BlockRule(null, null, null, 30, 600_000L, match, null), null);
        when(blockRepository.findAllByUser_IdOrderByDisplayOrderAsc(USER_ID)).thenReturn(List.of(rent));
        when(transactionRepository.sumBlockSpent(2L, SEP_1, LocalDateTime.of(2026, 10, 4, 0, 0)))
                .thenReturn(new BlockSpent(600_000L, 600_000L));
        lenient().when(transactionRepository.sumBlockSpent(eq(2L), eq(AUG_1), any())).thenReturn(new BlockSpent(0L, 0L));

        assertThat(fixedRate()).isEqualTo(100);
    }

    @Test
    @DisplayName("고정지출: 그 달 이후에 만든 블록은 빼고, 그 달 이후에 삭제한 블록은 넣는다")
    void fixedBlocksThatExistedThatMonth() {
        Block later = block(2L, BlockType.FIXED, "통신비", new BlockRule(null, null, null, 15, 55_000L, null, null), null);
        ReflectionTestUtils.setField(later, "startDate", LocalDate.of(2026, 10, 1));
        Block deletedAfter = block(3L, BlockType.FIXED, "월세", new BlockRule(null, null, null, 25, 600_000L, null, null), null);
        ReflectionTestUtils.setField(deletedAfter, "deletedAt", LocalDateTime.of(2026, 10, 5, 12, 0));
        Block deletedBefore = block(5L, BlockType.FIXED, "보험", new BlockRule(null, null, null, 10, 30_000L, null, null), null);
        ReflectionTestUtils.setField(deletedBefore, "deletedAt", LocalDateTime.of(2026, 8, 20, 12, 0));
        when(blockRepository.findAllByUser_IdOrderByDisplayOrderAsc(USER_ID))
                .thenReturn(List.of(later, deletedAfter, deletedBefore));
        when(transactionRepository.sumBlockSpent(3L, SEP_1, LocalDateTime.of(2026, 9, 26, 0, 0)))
                .thenReturn(new BlockSpent(600_000L, 600_000L));
        lenient().when(transactionRepository.sumBlockSpent(eq(3L), eq(AUG_1), any())).thenReturn(new BlockSpent(0L, 0L));
        lenient().when(transactionRepository.sumBlockSpent(eq(5L), eq(AUG_1), any())).thenReturn(new BlockSpent(0L, 0L));

        // 9월 대상은 삭제 전 월세 하나뿐이고 냈으니 100%
        assertThat(fixedRate()).isEqualTo(100);
    }

    @Test
    @DisplayName("그 달에 15일보다 짧게 썼거나 블록을 정하기 전이면 점수 없이 이유만")
    void tooShort() {
        usingSince(LocalDate.of(2026, 9, 20));
        MonthlyReportResponse.Score score = service.getMonthly(USER_ID, SEP).score();
        assertThat(score.total()).isNull();
        assertThat(score.note()).contains("15일");

        usingSince(null);
        assertThat(service.getMonthly(USER_ID, SEP).score().note()).contains("블록을 정한 뒤");
    }

    @Test
    @DisplayName("아직 오지 않은 달은 막는다")
    void futureMonth() {
        assertThatThrownBy(() -> service.getMonthly(USER_ID, YearMonth.now().plusMonths(2)))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ReportErrorCode.REPORT_FUTURE_MONTH);
    }
}
