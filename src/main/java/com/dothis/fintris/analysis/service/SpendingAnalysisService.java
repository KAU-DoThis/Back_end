package com.dothis.fintris.analysis.service;

import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.CategoryItem;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.CompareTo;
import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse.Period;
import com.dothis.fintris.transaction.repository.CategorySpent;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
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

    private final TransactionRepository transactionRepository;

    public SpendingAnalysisResponse getSpending(Long userId, YearMonth month) {
        LocalDate today = LocalDate.now(KST);
        YearMonth target = month != null ? month : YearMonth.from(today);
        boolean currentMonth = target.equals(YearMonth.from(today));
        Period period = new Period(target.atDay(1), currentMonth ? today : target.atEndOfMonth());
        CompareTo compareTo = compareTo(target.minusMonths(1), period.to().getDayOfMonth(), currentMonth);

        List<CategorySpent> current = sumByCategory(userId, period.from(), period.to());
        List<CategorySpent> previous = sumByCategory(userId, compareTo.from(), compareTo.to());
        boolean previousAvailable = !previous.isEmpty();

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
                previousAvailable ? changeRate(changeAmount, previousSpent) : null,
                categories, previousAvailable, current.isEmpty());
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
