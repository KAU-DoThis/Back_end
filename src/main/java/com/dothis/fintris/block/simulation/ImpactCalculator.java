package com.dothis.fintris.block.simulation;

import com.dothis.fintris.block.service.BlockCalculator;
import com.dothis.fintris.home.service.DailyGuideCalculator;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// 이동 전후 숫자로 결과 항목 문장을 만든다. AI 호출 없이 서버 계산값만 쓴다 (API 명세 v4 0장 ImpactItem)
public final class ImpactCalculator {

    private static final int DAYS_PER_MONTH = 30;

    private ImpactCalculator() {
    }

    // 사용형 하루 쓸 돈 (F-10과 같은 계산: 잔액 ÷ 남은 일수, 100원 내림)
    public static Optional<ImpactItem> dailyBudget(Long blockId, String name, long balanceBefore, long balanceAfter,
                                                   LocalDate dueDate, LocalDate today) {
        if (dueDate == null) {
            return Optional.empty();
        }
        long days = DailyGuideCalculator.remainingDays(today, dueDate);
        if (days == 0) {
            return Optional.empty();
        }
        long before = DailyGuideCalculator.todayAvailable(balanceBefore, days);
        long after = DailyGuideCalculator.todayAvailable(balanceAfter, days);
        if (before == after) {
            return Optional.empty();
        }
        return Optional.of(new ImpactItem(
                ImpactItem.Type.DAILY_BUDGET,
                after > before ? ImpactItem.Severity.GOOD : ImpactItem.Severity.WARN,
                blockId,
                "하루 쓸 돈 " + won(before) + "원 → " + won(after) + "원",
                name + " " + won(balanceAfter) + "원 남은 " + days + "일 기준",
                String.valueOf(before),
                String.valueOf(after)));
    }

    // 목표형: 예상 달성일이 바뀌면 goal_date, 날짜는 그대로고 이번 달 확보액만 바뀌면 monthly_secured
    // 같은 블록의 변화는 한 항목으로 합치고 확보액은 detail에 둔다
    public static Optional<ImpactItem> goal(Long blockId, String name, long targetAmount, Long monthlyTarget,
                                            long balanceBefore, long balanceAfter,
                                            long securedBefore, long securedAfter, LocalDate today) {
        String secured = "이번 달 확보액 " + won(securedBefore) + " → " + won(securedAfter)
                + (monthlyTarget != null ? " (월 목표 " + won(monthlyTarget) + ")" : "");

        LocalDate dateBefore = expectedDate(targetAmount, balanceBefore, monthlyTarget, today);
        LocalDate dateAfter = expectedDate(targetAmount, balanceAfter, monthlyTarget, today);
        if (dateBefore != null && dateAfter != null && !dateBefore.equals(dateAfter)) {
            long diff = ChronoUnit.DAYS.between(dateBefore, dateAfter);
            boolean later = diff > 0;
            return Optional.of(new ImpactItem(
                    ImpactItem.Type.GOAL_DATE,
                    later ? ImpactItem.Severity.WARN : ImpactItem.Severity.GOOD,
                    blockId,
                    name + " 목표가 약 " + span(Math.abs(diff)) + (later ? " 늦어져요" : " 빨라져요"),
                    secured,
                    dateBefore.toString(),
                    dateAfter.toString()));
        }
        if (securedBefore != securedAfter) {
            return Optional.of(new ImpactItem(
                    ImpactItem.Type.MONTHLY_SECURED,
                    securedAfter > securedBefore ? ImpactItem.Severity.GOOD : ImpactItem.Severity.WARN,
                    blockId,
                    name + " 이번 달 확보액 " + won(securedBefore) + " → " + won(securedAfter),
                    monthlyTarget != null ? "월 목표 " + won(monthlyTarget) : null,
                    String.valueOf(securedBefore),
                    String.valueOf(securedAfter)));
        }
        return Optional.empty();
    }

    // 고정지출형: 다음 납부일에 납부액을 확보했는지. 이번 이동으로 모자라지면 danger(한 번 더 확인)
    public static ImpactItem payment(Long blockId, String name, long payAmount, int payDay,
                                     long balanceBefore, long balanceAfter, LocalDate today) {
        LocalDate payDate = BlockCalculator.nextPayDate(today, payDay);
        String date = payDate.getMonthValue() + "월 " + payDate.getDayOfMonth() + "일";
        if (balanceAfter >= payAmount) {
            return new ImpactItem(ImpactItem.Type.PAYMENT, ImpactItem.Severity.GOOD, blockId,
                    name + " 납부 영향 없음",
                    date + " " + won(payAmount) + "원 확보 유지",
                    String.valueOf(balanceBefore), String.valueOf(balanceAfter));
        }
        long shortage = payAmount - balanceAfter;
        // 원래 모자랐고 이번 이동과 상관없으면 경고만 한다
        ImpactItem.Severity severity = balanceAfter < balanceBefore ? ImpactItem.Severity.DANGER : ImpactItem.Severity.WARN;
        return new ImpactItem(ImpactItem.Type.PAYMENT, severity, blockId,
                name + " " + date + "에 " + won(shortage) + "원 모자라요",
                "납부액 " + won(payAmount) + "원 중 " + won(balanceAfter) + "원 확보",
                String.valueOf(balanceBefore), String.valueOf(balanceAfter));
    }

    // 목표형 예상 달성일. 월 확보 금액을 하루치로 나눠 남은 금액을 채우는 날
    // TODO: v4는 최근 3개월 평균 확보액 기준. 배분 이력이 쌓이면 바꾼다. 지금은 월 확보 금액 기준
    public static LocalDate expectedDate(long targetAmount, long balance, Long monthlyTarget, LocalDate today) {
        long remaining = targetAmount - balance;
        if (remaining <= 0) {
            return today;
        }
        if (monthlyTarget == null || monthlyTarget <= 0) {
            return null;
        }
        long days = (remaining * DAYS_PER_MONTH + monthlyTarget - 1) / monthlyTarget;
        return today.plusDays(days);
    }

    public static List<ImpactItem> sorted(List<ImpactItem> items) {
        return items.stream().sorted(Comparator.comparing(ImpactItem::severity)).toList();
    }

    // 2주 이상은 주 단위로 반올림, 그보다 짧으면 일 단위
    public static String span(long days) {
        return days >= 14 ? Math.round(days / 7.0) + "주" : days + "일";
    }

    public static String won(long amount) {
        return String.format("%,d", amount);
    }
}
