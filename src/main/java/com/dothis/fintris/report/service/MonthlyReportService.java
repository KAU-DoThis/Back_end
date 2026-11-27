package com.dothis.fintris.report.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.repository.BlockTransferRepository;
import com.dothis.fintris.block.service.BlockCalculator;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.report.code.ReportErrorCode;
import com.dothis.fintris.report.dto.MonthlyReportResponse;
import com.dothis.fintris.report.dto.MonthlyReportResponse.AllocatedBlock;
import com.dothis.fintris.report.dto.MonthlyReportResponse.CategoryChange;
import com.dothis.fintris.report.dto.MonthlyReportResponse.DailyPoint;
import com.dothis.fintris.report.dto.MonthlyReportResponse.ScoreItem;
import com.dothis.fintris.report.service.ReportScoreCalculator.Item;
import com.dothis.fintris.saving.entity.SavingGoal;
import com.dothis.fintris.saving.entity.SavingGoalStatus;
import com.dothis.fintris.saving.repository.SavingGoalRepository;
import com.dothis.fintris.saving.service.SavingGoalCalculator;
import com.dothis.fintris.transaction.repository.CategorySpent;
import com.dothis.fintris.transaction.repository.DayTotal;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import com.dothis.fintris.user.entity.UserSetting;
import com.dothis.fintris.user.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 08 월간 리포트 (기능명세서 F-12·F-14, API 명세 v4 11장 GET /reports/monthly)
// 지출 요약·날짜별 누적·주요 소비 변화·절약 결과·금융관리 점수를 그때그때 계산한다 (저장은 아직 하지 않는다)
// 쓴 돈은 소비 분석·거래 목록과 같은 조건이다: 취소·소비 제외 거래는 빼고, 더치페이는 내 몫만 센다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyReportService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int MIN_USAGE_DAYS = 15; // 이용 기간이 15일보다 짧으면 점수는 보여 주지 않는다 (F-12 예외 처리)

    private final TransactionRepository transactionRepository;
    private final BlockRepository blockRepository;
    private final BlockTransferRepository blockTransferRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final UserSettingRepository userSettingRepository;

    // 집계 기간. 지난달 이전은 그 달 전체, 이번 달은 오늘까지
    record Period(YearMonth month, LocalDate from, LocalDate to) {
        LocalDate monthEnd() {
            return month.atEndOfMonth();
        }

        int days() {
            return (int) ChronoUnit.DAYS.between(from, to) + 1;
        }
    }

    public MonthlyReportResponse getMonthly(Long userId, YearMonth requested) {
        LocalDate today = LocalDate.now(KST);
        YearMonth current = YearMonth.from(today);
        YearMonth month = requested != null ? requested : current.minusMonths(1);
        if (month.isAfter(current)) {
            throw GeneralException.of(ReportErrorCode.REPORT_FUTURE_MONTH);
        }
        boolean inProgress = month.equals(current);
        Period period = new Period(month, month.atDay(1), inProgress ? today : month.atEndOfMonth());
        // 지난달은 같은 날짜까지 비교한다 (이번 달이 25일까지면 지난달도 25일까지, 지난달이 더 짧으면 말일까지)
        YearMonth prevMonth = month.minusMonths(1);
        Period previous = new Period(prevMonth, prevMonth.atDay(1),
                prevMonth.atDay(Math.min(period.to().getDayOfMonth(), prevMonth.lengthOfMonth())));
        if (!inProgress) {
            previous = new Period(prevMonth, prevMonth.atDay(1), prevMonth.atEndOfMonth());
        }

        MonthlyReportResponse.Spending spending = spending(userId, period, previous);
        List<CategoryChange> changes = categoryChanges(userId, period, previous);
        List<SavingGoal> confirmedGoals = confirmedGoals(userId, period);
        MonthlyReportResponse.Saving saving = saving(confirmedGoals);

        LocalDate usageStart = userSettingRepository.findByUser_Id(userId)
                .map(UserSetting::getBlocksConfirmedAt).map(LocalDateTime::toLocalDate).orElse(null);
        MonthlyReportResponse.Score score = score(userId, period, usageStart, confirmedGoals);

        return new MonthlyReportResponse(month.toString(),
                new MonthlyReportResponse.Period(period.from(), period.to()), inProgress,
                score, spending, saving, changes, comment(month, spending, saving, changes));
    }

    // ---------- 지출 ----------

    private MonthlyReportResponse.Spending spending(Long userId, Period period, Period previous) {
        long[] now = dailySpent(userId, period);
        long[] before = dailySpent(userId, previous);
        long total = sum(now);
        long previousTotal = sum(before);

        // 날짜별 누적: 1일부터 두 달 중 긴 쪽 말일까지. 이번 달 아직 안 온 날·그 달에 없는 날은 null
        int lastDay = Math.max(period.monthEnd().getDayOfMonth(), previous.monthEnd().getDayOfMonth());
        List<DailyPoint> daily = new ArrayList<>();
        long cumNow = 0;
        long cumBefore = 0;
        for (int day = 1; day <= lastDay; day++) {
            Long a = null;
            Long b = null;
            if (day <= now.length) {
                cumNow += now[day - 1];
                a = cumNow;
            }
            if (day <= before.length) {
                cumBefore += before[day - 1];
                b = cumBefore;
            }
            daily.add(new DailyPoint(day, a, b));
        }
        Integer changeRate = previousTotal > 0
                ? (int) ReportScoreCalculator.roundDiv(Math.multiplyExact(total - previousTotal, 100L), previousTotal)
                : null;
        return new MonthlyReportResponse.Spending(total, previousTotal, total - previousTotal, changeRate, daily);
    }

    // 기간 안의 날짜별 쓴 돈. 인덱스 0 = 기간 첫날
    private long[] dailySpent(Long userId, Period period) {
        long[] days = new long[period.days()];
        for (DayTotal row : transactionRepository.sumByDay(userId, period.from().atStartOfDay(),
                period.to().plusDays(1).atStartOfDay(), null, null, null)) {
            int index = (int) ChronoUnit.DAYS.between(period.from(), row.date());
            if (index >= 0 && index < days.length && row.total() != null) {
                days[index] += row.total();
            }
        }
        return days;
    }

    // 지난달 대비 차이가 큰 카테고리 3개 (같으면 이름순). 차이가 없는 카테고리는 뺀다
    private List<CategoryChange> categoryChanges(Long userId, Period period, Period previous) {
        Map<Long, long[]> amounts = new LinkedHashMap<>(); // [지난달, 이번 달]
        Map<Long, String> names = new LinkedHashMap<>();
        collect(userId, previous, 0, amounts, names);
        collect(userId, period, 1, amounts, names);
        return amounts.entrySet().stream()
                .map(e -> new CategoryChange(e.getKey(), names.get(e.getKey()), e.getValue()[0], e.getValue()[1],
                        e.getValue()[1] - e.getValue()[0]))
                .filter(change -> change.change() != 0)
                .sorted(Comparator.comparingLong((CategoryChange c) -> Math.abs(c.change())).reversed()
                        .thenComparing(CategoryChange::name, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(3)
                .toList();
    }

    private void collect(Long userId, Period period, int slot, Map<Long, long[]> amounts, Map<Long, String> names) {
        for (CategorySpent row : transactionRepository.sumSpentByCategory(userId, period.from().atStartOfDay(),
                period.to().plusDays(1).atStartOfDay())) {
            // 분류 전 거래는 categoryId가 null이라 "분류 전" 하나로 묶는다
            names.putIfAbsent(row.categoryId(), row.name() != null ? row.name() : "분류 전");
            amounts.computeIfAbsent(row.categoryId(), key -> new long[2])[slot] +=
                    row.amount() != null ? row.amount() : 0;
        }
    }

    // ---------- 절약 ----------

    private List<SavingGoal> confirmedGoals(Long userId, Period period) {
        return savingGoalRepository.findAllByUser_IdAndStatusAndEndDateBetween(userId, SavingGoalStatus.CONFIRMED,
                period.from(), period.monthEnd());
    }

    // 그 달에 끝나 확정된 절약 목표: 확정 절약액 합과 예상 절약액 합, 그 절약분을 블록에 나눈 내역
    private MonthlyReportResponse.Saving saving(List<SavingGoal> goals) {
        long confirmed = goals.stream().mapToLong(g -> g.getConfirmedSaving() != null ? g.getConfirmedSaving() : 0).sum();
        long expected = goals.stream()
                .mapToLong(g -> SavingGoalCalculator.expectedSaving(g.getBaseAmount(), g.getTargetAmount())).sum();
        List<Long> fundIds = goals.stream().map(SavingGoal::getWaitingFundId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, AllocatedBlock> byBlock = new LinkedHashMap<>();
        if (!fundIds.isEmpty()) {
            for (BlockTransfer transfer : blockTransferRepository.findAllocationsFrom(fundIds)) {
                Block to = transfer.getToBlock();
                if (to == null) {
                    continue;
                }
                byBlock.merge(to.getId(), new AllocatedBlock(to.getId(), to.getName(), transfer.getAmount()),
                        (a, b) -> new AllocatedBlock(a.blockId(), a.name(), a.amount() + b.amount()));
            }
        }
        long allocated = byBlock.values().stream().mapToLong(AllocatedBlock::amount).sum();
        return new MonthlyReportResponse.Saving(confirmed, expected, allocated, List.copyOf(byBlock.values()));
    }

    // ---------- 점수 ----------

    private MonthlyReportResponse.Score score(Long userId, Period period, LocalDate usageStart,
                                              List<SavingGoal> confirmedGoals) {
        if (usageStart == null) {
            return new MonthlyReportResponse.Score(null, null, null, "블록을 정한 뒤부터 점수를 계산해요.", List.of());
        }
        // 그 달 안에서 쓴 기간이 15일보다 짧으면 점수 없이 이유만 보여 준다
        LocalDate from = usageStart.isAfter(period.from()) ? usageStart : period.from();
        if (from.isAfter(period.to()) || ChronoUnit.DAYS.between(from, period.to()) + 1 < MIN_USAGE_DAYS) {
            return new MonthlyReportResponse.Score(null, null, null,
                    "이용한 지 " + MIN_USAGE_DAYS + "일이 안 돼서 점수는 다음 달부터 보여 드려요.", List.of());
        }
        List<Item> items = items(userId, period, from, confirmedGoals);
        Integer total = ReportScoreCalculator.total(items);

        // 지난달 점수: 지난달도 15일 이상 썼을 때만
        YearMonth prevMonth = period.month().minusMonths(1);
        Period prev = new Period(prevMonth, prevMonth.atDay(1), prevMonth.atEndOfMonth());
        LocalDate prevFrom = usageStart.isAfter(prev.from()) ? usageStart : prev.from();
        Integer previousTotal = null;
        if (!prevFrom.isAfter(prev.to()) && ChronoUnit.DAYS.between(prevFrom, prev.to()) + 1 >= MIN_USAGE_DAYS) {
            previousTotal = ReportScoreCalculator.total(items(userId, prev, prevFrom, confirmedGoals(userId, prev)));
        }
        Integer change = total != null && previousTotal != null ? total - previousTotal : null;
        return new MonthlyReportResponse.Score(total, previousTotal, change,
                total == null ? "점수를 낼 항목이 아직 없어요." : null,
                items.stream().map(i -> new ScoreItem(i.key(), i.label(), i.weight(), i.rate())).toList());
    }

    List<Item> items(Long userId, Period period, LocalDate from, List<SavingGoal> confirmedGoals) {
        // 그 달에 있었던 블록: 지금 있거나, 그 달 첫날 이후에 삭제한 블록 (소프트 삭제라 기록이 남아 있다)
        List<Block> blocks = blockRepository.findAllByUser_IdOrderByDisplayOrderAsc(userId).stream()
                .filter(b -> b.getDeletedAt() == null || !b.getDeletedAt().toLocalDate().isBefore(period.from()))
                .toList();
        long confirmed = confirmedGoals.stream()
                .mapToLong(g -> g.getConfirmedSaving() != null ? g.getConfirmedSaving() : 0).sum();
        long expected = confirmedGoals.stream()
                .mapToLong(g -> SavingGoalCalculator.expectedSaving(g.getBaseAmount(), g.getTargetAmount())).sum();
        return List.of(
                new Item("FIXED", "고정지출 확보", ReportScoreCalculator.FIXED_WEIGHT, fixedRate(blocks, period, from)),
                new Item("PACE", "생활비 페이스", ReportScoreCalculator.PACE_WEIGHT, paceRate(blocks, period, from)),
                // 절약 실천 = 확정 절약액 ÷ 예상 절약액 (최대 100%). 그 달에 끝난 절약 목표가 없으면 해당 없음
                new Item("SAVING", "절약 실천", ReportScoreCalculator.SAVING_WEIGHT,
                        ReportScoreCalculator.rate(confirmed, expected)),
                new Item("GOAL", "목표 달성률", ReportScoreCalculator.GOAL_WEIGHT, goalRate(blocks, period)));
    }

    // 고정지출 확보: 그 달 납부일(+허용 일수)까지 납부액만큼 낸 고정지출 수 ÷ 판정할 수 있는 고정지출 수
    private Integer fixedRate(List<Block> blocks, Period period, LocalDate from) {
        LocalDate today = LocalDate.now(KST);
        int due = 0;
        int paid = 0;
        for (Block block : blocks) {
            BlockRule rule = block.getRule();
            if (block.getType() != BlockType.FIXED || rule == null || rule.payDay() == null
                    || rule.payAmount() == null || rule.payAmount() <= 0) {
                continue;
            }
            LocalDate payDate = period.month().atDay(Math.min(rule.payDay(), period.month().lengthOfMonth()));
            // 아직 납부일이 안 왔거나, 쓰기 시작하기 전이거나, 블록을 만들기 전 납부일은 세지 않는다
            if (payDate.isAfter(period.to()) || payDate.isBefore(from) || block.getStartDate().isAfter(payDate)) {
                continue;
            }
            // 허용 일수만큼 납부일 뒤까지 본다. 다음 달로 넘어가도 그 납부로 인정한다 (9/30 + 3일 → 10/3까지)
            int window = rule.matchCondition() != null && rule.matchCondition().dayWindow() != null
                    ? rule.matchCondition().dayWindow() : 0;
            LocalDate deadline = payDate.plusDays(window);
            LocalDate until = deadline.isAfter(today) ? today : deadline;
            long amount = transactionRepository.sumBlockSpent(block.getId(), period.from().atStartOfDay(),
                    until.plusDays(1).atStartOfDay()).spent();
            boolean ok = amount >= rule.payAmount();
            // 아직 허용 기간이 남았는데 안 냈으면 미납으로 판정하지 않는다
            if (!ok && deadline.isAfter(today)) {
                continue;
            }
            due++;
            if (ok) {
                paid++;
            }
        }
        return ReportScoreCalculator.rate(paid, due);
    }

    // 생활비 페이스: 기본 사용형 블록이 그 달 중 안전이었던 날의 비율. 월 배분액을 정하지 않았으면 해당 없음
    private Integer paceRate(List<Block> blocks, Period period, LocalDate from) {
        Block living = blocks.stream().filter(Block::isDefault).findFirst().orElse(null);
        if (living == null || living.getRule() == null || living.getRule().monthlyAmount() == null) {
            return null;
        }
        long[] daily = new long[(int) ChronoUnit.DAYS.between(period.from(), period.to()) + 1];
        transactionRepository.sumBlockSpentByDay(living.getId(), period.from().atStartOfDay(),
                period.to().plusDays(1).atStartOfDay()).forEach(row -> {
                    int index = (int) ChronoUnit.DAYS.between(period.from(), row.date());
                    if (index >= 0 && index < daily.length && row.total() != null) {
                        daily[index] += row.total();
                    }
                });
        // 쓰기 시작한 날부터 센다 (그 전 날짜는 판정하지 않는다)
        int skip = (int) ChronoUnit.DAYS.between(period.from(), from);
        long before = 0;
        for (int i = 0; i < skip; i++) {
            before += daily[i];
        }
        long[] counted = new long[daily.length - skip];
        System.arraycopy(daily, skip, counted, 0, counted.length);
        if (counted.length > 0) {
            counted[0] += before;
        }
        return ReportScoreCalculator.paceRate(counted, living.getRule().monthlyAmount(), period.from(),
                period.monthEnd(), from);
    }

    // 목표 달성률: 목표형 블록마다 (그 달 확보액 ÷ 월 확보 금액, 최대 100%)의 평균
    // 월 확보 금액이 0(다 모아서 멈춤)이거나 정할 수 없는 블록, 그 달 이후에 만든 블록은 뺀다
    private Integer goalRate(List<Block> blocks, Period period) {
        LocalDateTime monthStart = period.from().atStartOfDay();
        LocalDateTime nextMonth = period.monthEnd().plusDays(1).atStartOfDay();
        long sumRate = 0;
        int count = 0;
        for (Block block : blocks) {
            if (block.getType() != BlockType.GOAL || block.getStartDate().isAfter(period.monthEnd())) {
                continue;
            }
            Long monthly = monthlyTarget(block, period.from());
            if (monthly == null || monthly <= 0) {
                continue;
            }
            long secured = secured(block.getId(), monthStart) - secured(block.getId(), nextMonth);
            sumRate += ReportScoreCalculator.rate(secured, monthly);
            count++;
        }
        return count == 0 ? null : (int) (sumRate / count);
    }

    private long secured(Long blockId, LocalDateTime from) {
        return blockTransferRepository.sumInSince(blockId, from) - blockTransferRepository.sumOutSince(blockId, from);
    }

    private Long monthlyTarget(Block block, LocalDate monthStart) {
        BlockRule rule = block.getRule();
        if (rule != null && rule.monthlyTarget() != null) {
            return rule.monthlyTarget();
        }
        if (block.getTargetAmount() == null || block.getDueDate() == null || block.getDueDate().isBefore(monthStart)) {
            return null;
        }
        return BlockCalculator.recommendedMonthlyTarget(block.getTargetAmount(), block.getBalance(), monthStart,
                block.getDueDate());
    }

    // ---------- 한 달 정리 ----------

    // TODO: AI 서버에 한 달 정리 문장 API가 생기면 AI 문장으로 바꾸고, 숫자가 다르면 이 템플릿을 쓴다 (F-13)
    static MonthlyReportResponse.Comment comment(YearMonth month, MonthlyReportResponse.Spending spending,
                                                 MonthlyReportResponse.Saving saving, List<CategoryChange> changes) {
        StringBuilder text = new StringBuilder();
        text.append(month.getMonthValue()).append("월에는 ").append(won(spending.total())).append("원을 썼어요.");
        long diff = spending.changeAmount();
        if (spending.previousTotal() > 0 && diff != 0) {
            text.append(" 지난달보다 ").append(won(Math.abs(diff))).append(diff < 0 ? "원 줄었어요." : "원 늘었어요.");
        }
        if (saving.confirmed() > 0) {
            text.append(" 절약 목표로 ").append(won(saving.confirmed())).append("원을 모았어요.");
        }
        changes.stream().filter(c -> c.change() > 0).findFirst().ifPresent(c ->
                text.append(" 대신 ").append(c.name()).append(" 지출이 ").append(won(c.change()))
                        .append("원 늘었어요. 다음 달 절약 후보로 같이 볼까요?"));
        return new MonthlyReportResponse.Comment(text.toString(), "TEMPLATE");
    }

    private static String won(long amount) {
        return String.format("%,d", amount);
    }

    private static long sum(long[] values) {
        long total = 0;
        for (long v : values) {
            total += v;
        }
        return total;
    }
}
