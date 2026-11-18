package com.dothis.fintris.analysis.service;

import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.BiggestIncrease;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.CategoryItem;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.CompareTo;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.Period;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.Recurring;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.TimeOfDay;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.repository.CategorySpent;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 소비 분석 (기능명세서 F-01). 쓴 돈은 거래 목록과 같은 기준(출금 중 취소·소비 제외가 아닌 것)으로 센다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpendingAnalysisService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final String UNCLASSIFIED_CODE = "UNCLASSIFIED";
    static final String UNCLASSIFIED_NAME = "미분류";
    private static final int RECURRING_HISTORY_MONTHS = 3;

    private final TransactionRepository transactionRepository;
    private final RecurringSpendingFinder recurringSpendingFinder;

    public SpendingAnalysisResponse getSpending(Long userId, YearMonth month) {
        LocalDate today = LocalDate.now(KST);
        YearMonth target = month != null ? month : YearMonth.from(today);
        boolean currentMonth = target.equals(YearMonth.from(today));
        Period period = new Period(target.atDay(1), currentMonth ? today : target.atEndOfMonth());
        CompareTo compareTo = compareTo(target.minusMonths(1), period.to().getDayOfMonth(), currentMonth);

        List<CategorySpent> current = sumByCategory(userId, period.from(), period.to());
        List<CategorySpent> previous = sumByCategory(userId, compareTo.from(), compareTo.to());
        boolean previousAvailable = !previous.isEmpty();

        Map<Long, CategorySpent> previousSpents = new HashMap<>();
        previous.forEach(spent -> previousSpents.put(spent.categoryId(), spent));
        Map<Long, Long> previousByCategory = new HashMap<>();
        previous.forEach(spent -> previousByCategory.put(spent.categoryId(), spent.amount()));

        List<CategoryItem> categories = current.stream()
                .sorted(Comparator.comparingLong(CategorySpent::amount).reversed())
                .map(spent -> new CategoryItem(spent.categoryId(),
                        spent.code() != null ? spent.code() : UNCLASSIFIED_CODE,
                        spent.name() != null ? spent.name() : UNCLASSIFIED_NAME,
                        spent.count(), spent.amount(),
                        previousAvailable ? spent.amount() - previousByCategory.getOrDefault(spent.categoryId(), 0L) : null))
                .toList();

        long totalSpent = total(current);
        Long previousSpent = previousAvailable ? total(previous) : null;
        Long changeAmount = previousAvailable ? totalSpent - previousSpent : null;
        return new SpendingAnalysisResponse(period, compareTo, totalSpent, previousSpent, changeAmount,
                previousAvailable ? changeRate(changeAmount, previousSpent) : null, categories,
                previousAvailable ? biggestIncrease(userId, period, current, previousByCategory, previousSpents) : null,
                recurring(userId, period), previousAvailable, current.isEmpty());
    }

    // 고정지출은 서로 다른 달 3번 이상이라 기간 앞 3달치를 함께 본다
    private List<Recurring> recurring(Long userId, Period period) {
        List<Transaction> history = transactionRepository.findSpent(userId,
                period.from().minusMonths(RECURRING_HISTORY_MONTHS).atStartOfDay(), period.to().plusDays(1).atStartOfDay());
        return recurringSpendingFinder.find(history, period.from());
    }

    // 미분류는 무엇이 늘었는지 말할 수 없어 뺀다
    private BiggestIncrease biggestIncrease(Long userId, Period period, List<CategorySpent> current,
                                            Map<Long, Long> previousByCategory, Map<Long, CategorySpent> previousSpents) {
        CategorySpent top = null;
        long topChange = 0;
        for (CategorySpent spent : current) {
            long change = spent.amount() - previousByCategory.getOrDefault(spent.categoryId(), 0L);
            if (spent.categoryId() != null && change > topChange) {
                top = spent;
                topChange = change;
            }
        }
        if (top == null) {
            return null;
        }

        long previousAmount = previousByCategory.getOrDefault(top.categoryId(), 0L);
        List<LocalDateTime> times = transactionRepository.findSpentTimes(userId, top.categoryId(),
                period.from().atStartOfDay(), period.to().plusDays(1).atStartOfDay());
        Map<TimeSlot, Long> counts = new EnumMap<>(TimeSlot.class);
        times.forEach(time -> counts.merge(TimeSlot.of(time.toLocalTime()), 1L, Long::sum));
        List<TimeOfDay> timeOfDay = Arrays.stream(TimeSlot.values())
                .map(slot -> new TimeOfDay(slot.name(), slot.getLabel(), counts.getOrDefault(slot, 0L)))
                .toList();

        CategorySpent before = previousSpents.get(top.categoryId());
        String explanation = explanation(before == null ? 0 : before.count(), top.count(), previousAmount, top.amount());
        return new BiggestIncrease(top.categoryId(), top.name(), topChange, changeRate(topChange, previousAmount),
                previousAmount, explanation, timeOfDay, timeSummary(times.size(), counts), null);
    }

    // TODO: AI 설명 문장(/internal/ai/spending-comment) 형식이 정해지면 AI 문장을 먼저 쓰고, 실패하면 이 틀을 쓴다
    // 횟수와 건당 금액 중 무엇 때문에 늘었는지 말한다. 건당 금액은 ±10% 안이면 비슷하다고 본다
    static String explanation(long previousCount, long count, long previousAmount, long amount) {
        if (previousCount == 0) {
            return "지난 기간엔 없던 지출이 " + count + "번 생겼어요.";
        }
        // 건당 금액 비교: amount/count vs previousAmount/previousCount 를 곱셈으로 비교한다
        long now = amount * previousCount;
        long before = previousAmount * count;
        String perPayment = Math.abs(now - before) * 100 <= before * 10 ? "비슷해요"
                : now > before ? "더 커졌어요" : "더 작아졌어요";
        if (count > previousCount) {
            return "횟수가 " + previousCount + "번에서 " + count + "번으로 늘었고, 한 번에 쓰는 돈은 " + perPayment + ".";
        }
        String countPart = count == previousCount ? "횟수는 " + count + "번으로 같지만"
                : "횟수는 " + previousCount + "번에서 " + count + "번으로 줄었지만";
        return countPart + ", 한 번에 쓰는 돈이 커졌어요.";
    }

    // 한 시간대에 절반 이상 몰렸을 때만 말한다. 고르게 퍼져 있으면 null
    static String timeSummary(long total, Map<TimeSlot, Long> counts) {
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(top -> top.getValue() * 2 >= total)
                .map(top -> total + "번 중 " + top.getValue() + "번이 " + top.getKey().getSummaryPhrase() + "였어요")
                .orElse(null);
    }

    // 이번 달은 지난달 같은 날짜까지 비교한다. 지난달에 그 날짜가 없으면(3/31 → 2월) 말일까지
    static CompareTo compareTo(YearMonth previousMonth, int lastDay, boolean currentMonth) {
        LocalDate to = currentMonth
                ? previousMonth.atDay(Math.min(lastDay, previousMonth.lengthOfMonth()))
                : previousMonth.atEndOfMonth();
        String label = previousMonth.getMonthValue() + (currentMonth ? "월 같은 기간" : "월");
        return new CompareTo(previousMonth.atDay(1), to, label);
    }

    // 지난 기간이 0원이면 비율을 낼 수 없어 null
    static Integer changeRate(long changeAmount, long previousSpent) {
        return previousSpent > 0 ? (int) Math.round(changeAmount * 100.0 / previousSpent) : null;
    }

    private List<CategorySpent> sumByCategory(Long userId, LocalDate from, LocalDate to) {
        return transactionRepository.sumSpentByCategory(userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    private long total(List<CategorySpent> spents) {
        return spents.stream().mapToLong(CategorySpent::amount).sum();
    }
}
