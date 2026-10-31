package com.dothis.fintris.home.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// F-10 일별 생활비 가이드 계산. 홈 화면과 아침 알림(S4)이 같은 계산을 쓴다
// 기준 금액 = 기본 사용형 블록 잔액 ÷ 기간 남은 일수(오늘 포함), 100원 단위 내림
public final class DailyGuideCalculator {

    private static final long UNIT = 100;

    private DailyGuideCalculator() {
    }

    // 기간이 이미 끝났으면 0
    public static long remainingDays(LocalDate today, LocalDate periodEnd) {
        if (periodEnd.isBefore(today)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(today, periodEnd) + 1;
    }

    // 잔액이 0 이하이거나 남은 일수가 없으면 0
    public static long todayAvailable(long balance, long remainingDays) {
        if (balance <= 0 || remainingDays <= 0) {
            return 0;
        }
        return balance / remainingDays / UNIT * UNIT;
    }
}
