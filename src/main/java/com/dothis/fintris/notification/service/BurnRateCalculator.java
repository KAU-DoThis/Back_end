package com.dothis.fintris.notification.service;

import com.dothis.fintris.notification.dto.BurnRateDetailResponse.DayAmount;
import com.dothis.fintris.notification.dto.BurnRateDetailResponse.Depletion;
import com.dothis.fintris.notification.dto.BurnRateDetailResponse.TopIncrease;
import com.dothis.fintris.transaction.repository.CategorySpent;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 소진 속도 경고 계산 (13-1). 최근 = 오늘 포함 7일, 평소 = 그 앞 4주(28일)
// 평소는 블록 반영 기록이 짧은 사용자가 많아 블록이 아니라 같은 카테고리의 쓴 돈으로 본다
final class BurnRateCalculator {

    static final int RECENT_DAYS = 7;
    static final int USUAL_DAYS = 28;
    private static final int TOP_LIMIT = 3;
    private static final String UNCLASSIFIED_NAME = "미분류";

    private BurnRateCalculator() {
    }

    static List<DayAmount> series(Map<LocalDate, Long> spentByDate, LocalDate today, Long usualDailyAvg) {
        List<DayAmount> series = new ArrayList<>();
        for (LocalDate date = today.minusDays(RECENT_DAYS - 1); !date.isAfter(today); date = date.plusDays(1)) {
            long amount = spentByDate.getOrDefault(date, 0L);
            series.add(new DayAmount(date, amount, usualDailyAvg != null && amount > usualDailyAvg));
        }
        return series;
    }

    // 평소 = 블록이 최근 7일에 받은 카테고리들이 직전 4주에 쓴 돈 ÷ 28. 그 카테고리 기록이 없으면 null
    static Long usualDailyAvg(List<CategorySpent> recentInBlock, List<CategorySpent> usualAll) {
        Map<Long, Long> usual = byCategory(usualAll);
        long total = 0;
        boolean found = false;
        for (CategorySpent spent : recentInBlock) {
            Long amount = usual.get(spent.categoryId());
            if (amount != null) {
                total += amount;
                found = true;
            }
        }
        return found ? total / USUAL_DAYS : null;
    }

    // 평소보다 빠른 비율(%). 평소를 모르거나 0원이면 null
    static Integer fasterRate(long dailyAvg, Long usualDailyAvg) {
        if (usualDailyAvg == null || usualDailyAvg == 0) {
            return null;
        }
        return (int) Math.round((dailyAvg - usualDailyAvg) * 100.0 / usualDailyAvg);
    }

    static String headline(Integer fasterRate, long dailyAvg) {
        if (fasterRate == null || fasterRate <= 0) {
            return "최근 7일 하루 평균 %,d원을 썼어요".formatted(dailyAvg);
        }
        return "평소보다 " + fasterRate + "% 빨리 줄고 있어요";
    }

    // 최근 하루 평균으로 잔액이 바닥나는 날. 기간 안에 바닥나지 않거나 쓴 돈이 없으면 null
    static Depletion depletion(long balance, long dailyAvg, LocalDate today, LocalDate dueDate) {
        if (dailyAvg <= 0 || dueDate == null) {
            return null;
        }
        LocalDate date = today.plusDays(Math.max(0, balance) / dailyAvg);
        if (!date.isBefore(dueDate)) {
            return null;
        }
        return new Depletion(date, ChronoUnit.DAYS.between(date, dueDate));
    }

    // F-10 오늘 쓸 수 있는 돈과 같은 방식: 잔액 ÷ 남은 일수(오늘 포함), 100원 내림
    static long safeDaily(long balance, long remainingDays) {
        if (balance <= 0 || remainingDays <= 0) {
            return 0;
        }
        return balance / remainingDays / 100 * 100;
    }

    // 카테고리별로 최근 7일에 쓴 돈 − 평소 일주일(4주 합 ÷ 4). 더 쓴 곳만, 많이 더 쓴 순
    static List<TopIncrease> topIncreases(List<CategorySpent> recentInBlock, List<CategorySpent> usualAll) {
        Map<Long, Long> usual = byCategory(usualAll);
        return recentInBlock.stream()
                .map(spent -> {
                    long usualWeek = usual.getOrDefault(spent.categoryId(), 0L) * RECENT_DAYS / USUAL_DAYS;
                    String label = spent.name() != null ? spent.name() : UNCLASSIFIED_NAME;
                    return new TopIncrease(label, spent.count() + "건", spent.amount() - usualWeek);
                })
                .filter(increase -> increase.extraAmount() > 0)
                .sorted(Comparator.comparingLong(TopIncrease::extraAmount).reversed())
                .limit(TOP_LIMIT)
                .toList();
    }

    // 분류 전 거래(categoryId=null)도 하나의 묶음으로 본다
    private static Map<Long, Long> byCategory(List<CategorySpent> spents) {
        Map<Long, Long> amounts = new HashMap<>();
        spents.forEach(spent -> amounts.merge(spent.categoryId(), spent.amount(), Long::sum));
        return amounts;
    }
}
