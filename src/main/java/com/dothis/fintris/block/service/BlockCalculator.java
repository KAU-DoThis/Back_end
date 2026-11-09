package com.dothis.fintris.block.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockRule;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

// API 명세 v4 블록 객체의 계산 필드 (capacity, fillRate, dDay, statusSubLabel)와 목표형 권장 월 확보 금액
public final class BlockCalculator {

    private BlockCalculator() {
    }

    // 비커 크기 기준 금액. 사용형=월 배분액, 목표형=목표 금액, 고정지출형=납부액. 정하지 않았으면 null
    public static Long capacity(Block block) {
        BlockRule rule = block.getRule();
        return switch (block.getType()) {
            case SPENDING -> (rule != null) ? rule.monthlyAmount() : null;
            case GOAL -> block.getTargetAmount();
            case FIXED -> (rule != null) ? rule.payAmount() : null;
        };
    }

    // 물 높이(%) = 잔액 ÷ capacity, 0~100으로 자르고 내림
    public static Integer fillRate(long balance, Long capacity) {
        if (capacity == null || capacity <= 0) {
            return null;
        }
        long rate = balance * 100 / capacity;
        return (int) Math.max(0, Math.min(100, rate));
    }

    // 고정지출형=다음 납부일까지, 목표형=목표일까지 남은 일수(지났으면 음수). 사용형은 null
    public static Long dDay(Block block, LocalDate today) {
        return switch (block.getType()) {
            case SPENDING -> null;
            case GOAL -> (block.getDueDate() != null)
                    ? ChronoUnit.DAYS.between(today, block.getDueDate())
                    : null;
            case FIXED -> {
                Integer payDay = (block.getRule() != null) ? block.getRule().payDay() : null;
                yield (payDay != null) ? ChronoUnit.DAYS.between(today, nextPayDate(today, payDay)) : null;
            }
        };
    }

    // 오늘을 포함한 다음 납부일. 그 달에 납부일이 없으면(31일 등) 말일로 본다
    public static LocalDate nextPayDate(LocalDate today, int payDay) {
        LocalDate thisMonth = payDateIn(YearMonth.from(today), payDay);
        return thisMonth.isBefore(today) ? payDateIn(YearMonth.from(today).plusMonths(1), payDay) : thisMonth;
    }

    private static LocalDate payDateIn(YearMonth month, int payDay) {
        return month.atDay(Math.min(payDay, month.lengthOfMonth()));
    }

    // 상태 칩 옆 짧은 이유. 상태 판정(F-11)이 붙기 전까지는 금액만으로 알 수 있는 경우만 채운다
    public static String statusSubLabel(Block block) {
        Long capacity = capacity(block);
        if (capacity == null || capacity <= 0 || block.getBalance() < capacity) {
            return null;
        }
        return switch (block.getType()) {
            case GOAL -> "목표 달성";
            case FIXED -> "납부 준비 끝";
            case SPENDING -> null;
        };
    }

    // 목표형 월 확보 금액 권장값 = 남은 금액 ÷ 남은 개월 수, 올림 (F-07)
    // TBD: 남은 개월은 지금은 달력 월로 센다(이번 달 포함). 팀 결정에 따라 바뀔 수 있다
    public static long recommendedMonthlyTarget(long targetAmount, long balance, LocalDate today, LocalDate dueDate) {
        long remaining = Math.max(0, targetAmount - balance);
        long months = remainingMonths(today, dueDate);
        if (months <= 0) {
            return remaining;
        }
        return (remaining + months - 1) / months;
    }

    // 이번 달부터 기한이 있는 달까지 (둘 다 포함). 기한이 지났으면 0
    public static long remainingMonths(LocalDate today, LocalDate dueDate) {
        if (dueDate.isBefore(today)) {
            return 0;
        }
        return ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(dueDate)) + 1;
    }
}
