package com.dothis.fintris.goal.service;

import com.dothis.fintris.block.simulation.ImpactCalculator;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

// 목표 목록 계산 (기능명세서 F-07). 같은 입력이면 같은 결과가 나오도록 날짜를 받는다
public final class GoalCalculator {

    static final int PACE_MONTHS = 3; // 예상 달성 시점은 최근 3개월 평균 확보액 기준

    private GoalCalculator() {
    }

    // 달성률 = 잔액 ÷ 목표 금액, 0~100으로 자르고 내림
    public static int achievementRate(long balance, long targetAmount) {
        if (targetAmount <= 0) {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, balance * 100 / targetAmount));
    }

    // 평균을 낼 지난 달 수. 블록을 만든 달은 첫 배분이 섞여 빼고, 그 다음 달부터 지난달까지 최대 3개월
    public static int paceMonths(LocalDate startDate, LocalDate today) {
        long full = ChronoUnit.MONTHS.between(YearMonth.from(startDate).plusMonths(1), YearMonth.from(today));
        return (int) Math.max(0, Math.min(PACE_MONTHS, full));
    }

    // 예상 달성 시점에 쓸 월 확보액. 지난 달 평균이 있으면 그 값, 없거나 0 이하면 월 확보 금액
    public static Long paceMonthly(long securedInPastMonths, int months, Long monthlyTarget) {
        if (months > 0 && securedInPastMonths > 0) {
            return securedInPastMonths / months;
        }
        return monthlyTarget;
    }

    // 늦어지는 일수. 목표일보다 빠르면 0
    public static Long delayDays(LocalDate dueDate, LocalDate paceDate) {
        if (dueDate == null || paceDate == null) {
            return null;
        }
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, paceDate));
    }

    public static LocalDate paceDate(long targetAmount, long balance, Long paceMonthly, LocalDate today) {
        return ImpactCalculator.expectedDate(targetAmount, balance, paceMonthly, today);
    }
}
