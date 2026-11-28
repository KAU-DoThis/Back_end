package com.dothis.fintris.report.service;

import com.dothis.fintris.block.entity.BlockStatus;
import com.dothis.fintris.block.service.BlockStatusJudge;

import java.time.LocalDate;
import java.util.List;

// 금융관리 점수 (기능명세서 F-14). 항목별 달성률(0~100%)에 배점을 곱해 더한다
// 배점은 팀 결정(11/26)으로 생활비 페이스를 올리고 절약 실천을 내렸다: 지키기(고정지출·생활비) 60 + 모으기(절약·목표) 40
// 해당 항목이 없으면(rate == null) 그 배점은 나머지 항목에 비율대로 나눈다
public final class ReportScoreCalculator {

    public static final int FIXED_WEIGHT = 30;  // 고정지출 확보
    public static final int PACE_WEIGHT = 30;   // 생활비 페이스 준수
    public static final int SAVING_WEIGHT = 15; // 절약 실천
    public static final int GOAL_WEIGHT = 25;   // 목표 월 확보

    private ReportScoreCalculator() {
    }

    public record Item(String key, String label, int weight, Integer rate) {
    }

    // 점수 = Σ(달성률 × 배점) ÷ Σ(해당 항목 배점), 반올림. 해당 항목이 하나도 없으면 null
    public static Integer total(List<Item> items) {
        long weighted = 0;
        long weights = 0;
        for (Item item : items) {
            if (item.rate() != null) {
                weighted += (long) item.rate() * item.weight();
                weights += item.weight();
            }
        }
        return weights == 0 ? null : (int) roundDiv(weighted, weights);
    }

    // 비율(%) = 분자 ÷ 분모, 반올림하고 0~100으로 자른다 (시안: 87,000 ÷ 98,000 → 89%). 분모가 0이면 null
    public static Integer rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return (int) Math.max(0, Math.min(100, roundDiv(Math.multiplyExact(numerator, 100L), denominator)));
    }

    // 생활비 페이스: 날마다 그날까지 쓴 돈으로 상태 판정(F-11 사용형)을 다시 돌려 안전이었던 날의 비율
    // dailySpent[i] = from + i일에 쓴 돈. 상태를 날마다 저장하지 않아서 다시 계산한다
    public static Integer paceRate(long[] dailySpent, long monthlyAmount, LocalDate periodStart, LocalDate periodEnd,
                                   LocalDate from) {
        if (monthlyAmount <= 0 || dailySpent.length == 0) {
            return null;
        }
        long cumulative = 0;
        int safeDays = 0;
        for (int i = 0; i < dailySpent.length; i++) {
            cumulative += dailySpent[i];
            // 배분액을 넘겨 썼으면 안전이 아니다. 딱 맞게 다 쓴 날(잔액 0원)은 "잔액 없음"으로 보지 않고
            // 소진율 - 경과율로만 판정한다 (마지막 날 예산을 다 쓴 건 잘 지킨 것)
            if (cumulative > monthlyAmount) {
                continue;
            }
            BlockStatus status = BlockStatusJudge.spending(Math.max(1, monthlyAmount - cumulative), monthlyAmount,
                    periodStart, periodEnd, from.plusDays(i), cumulative).status();
            if (status == BlockStatus.SAFE) {
                safeDays++;
            }
        }
        return rate(safeDays, dailySpent.length);
    }

    // 정수 나눗셈 반올림 (부동소수점을 쓰지 않는다)
    public static long roundDiv(long numerator, long denominator) {
        long half = denominator / 2;
        return numerator >= 0 ? (numerator + half) / denominator : -((-numerator + half) / denominator);
    }
}
