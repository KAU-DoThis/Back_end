package com.dothis.fintris.saving.service;

import java.time.LocalDate;
import java.time.YearMonth;

// 절약 목표 계산 (기능명세서 F-03). 금액은 원 단위 정수로만 계산한다
// 기준은 팀 결정(11/25)에 따라 "지난달"이다. 절약 후보도 지난달보다 늘어난 만큼 줄이도록 제안한다
public final class SavingGoalCalculator {

    private SavingGoalCalculator() {
    }

    // 기준을 셀 기간: 시작일이 있는 달의 직전 달 한 달 (달력 월)
    public static LocalDate baseFrom(LocalDate startDate) {
        return YearMonth.from(startDate).minusMonths(1).atDay(1);
    }

    public static LocalDate baseTo(LocalDate startDate) {
        return YearMonth.from(startDate).atDay(1);
    }

    // 1회 평균 금액 = 기준 금액 ÷ 기준 횟수, 내림. 지난달에 쓴 적이 없으면 null
    public static Long averageAmount(long baseAmount, long baseCount) {
        if (baseCount <= 0) {
            return null;
        }
        return baseAmount / baseCount;
    }

    // 횟수로 정한 목표를 금액으로 바꾼다 = 기준 금액 × 목표 횟수 ÷ 기준 횟수, 내림
    // 1회 평균을 먼저 내림하지 않고 한 번에 나눠서, 목표 횟수가 기준과 같으면 기준 금액과 정확히 같아진다
    public static long targetAmountFor(long baseAmount, long baseCount, long targetCount) {
        if (baseCount <= 0) {
            return 0;
        }
        return Math.multiplyExact(baseAmount, targetCount) / baseCount;
    }

    // 예상 절약액 = 기준 - 목표 (0 미만이면 0)
    public static long expectedSaving(long baseAmount, long targetAmount) {
        return Math.max(0, baseAmount - targetAmount);
    }

    // 확정 절약액 = 기준 - 실제 (0 미만이면 0). 목표를 넘겨도 기준보다 적게 썼으면 그만큼 인정한다
    public static long confirmedSaving(long baseAmount, long actualAmount) {
        return Math.max(0, baseAmount - actualAmount);
    }

    // 진행률(%) = 쓴 돈 ÷ 목표 지출액, 내림. 100을 넘을 수 있어 int로 줄이지 않는다. 목표가 0원이면 null
    public static Long progressRate(long spent, long targetAmount) {
        if (targetAmount <= 0) {
            return null;
        }
        return Math.multiplyExact(Math.max(0, spent), 100L) / targetAmount;
    }

    // 생활비가 모자라 절약액 합(total)보다 적게(movable) 옮길 때 카테고리별 내역도 같은 비율로 줄인다
    // 각 몫은 내림하고, 남는 원 단위는 가장 많이 아낀 카테고리에 더해 합이 movable과 정확히 같게 한다
    public static long[] scaleDown(long[] savings, long total, long movable) {
        long[] scaled = savings.clone();
        if (movable >= total || total <= 0) {
            return scaled;
        }
        long sum = 0;
        int largest = 0;
        for (int i = 0; i < savings.length; i++) {
            scaled[i] = Math.multiplyExact(savings[i], movable) / total;
            sum += scaled[i];
            if (savings[i] > savings[largest]) {
                largest = i;
            }
        }
        scaled[largest] += movable - sum;
        return scaled;
    }

    // 기간을 안 보내면 시작일부터 1개월 (예: 10/15 ~ 11/14)
    public static LocalDate defaultEndDate(LocalDate startDate) {
        return startDate.plusMonths(1).minusDays(1);
    }
}
