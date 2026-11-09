package com.dothis.fintris.block.service;

import com.dothis.fintris.block.dto.BlockDetailResponse;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 사용형 블록의 소진 속도 (블록 상세 02 "계획보다 17% 빠름" 그래프)
// 계획: 월 배분액을 기간 일수로 고르게 나눈 누적, 실제: 이 블록에 반영된 거래 금액의 누적
final class BurnCalculator {

    private BurnCalculator() {
    }

    // spentByDate: 날짜별 쓴 돈. 월 배분액이 없거나 기간을 모르면 null
    static BlockDetailResponse.Burn calculate(Long monthlyAmount, LocalDate start, LocalDate end, LocalDate today,
                                              long balance, Map<LocalDate, Long> spentByDate) {
        if (monthlyAmount == null || monthlyAmount <= 0 || start == null || end == null || today.isBefore(start)) {
            return null;
        }
        long periodDays = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate last = today.isAfter(end) ? end : today;

        List<BlockDetailResponse.BurnPoint> series = new ArrayList<>();
        long actual = 0;
        long planned = 0;
        for (LocalDate date = start; !date.isAfter(last); date = date.plusDays(1)) {
            long dayIndex = ChronoUnit.DAYS.between(start, date) + 1;
            planned = monthlyAmount * dayIndex / periodDays;
            actual += spentByDate.getOrDefault(date, 0L);
            series.add(new BlockDetailResponse.BurnPoint(date, planned, actual));
        }

        Integer paceDiffRate = planned > 0 ? (int) Math.round((actual - planned) * 100.0 / planned) : null;
        return new BlockDetailResponse.Burn(paceDiffRate, paceLabel(paceDiffRate), actual, series,
                depletionDate(balance, actual, ChronoUnit.DAYS.between(start, last) + 1, last, end));
    }

    static String paceLabel(Integer paceDiffRate) {
        if (paceDiffRate == null) {
            return null;
        }
        if (paceDiffRate == 0) {
            return "계획대로";
        }
        return "계획보다 " + Math.abs(paceDiffRate) + "% " + (paceDiffRate > 0 ? "빠름" : "느림");
    }

    // 지금까지의 하루 평균으로 남은 잔액이 바닥나는 날. 기간 안에 바닥나지 않으면 null
    private static LocalDate depletionDate(long balance, long actual, long elapsedDays, LocalDate last, LocalDate end) {
        if (actual <= 0) {
            return null;
        }
        long dailyAverage = Math.max(1, actual / elapsedDays);
        LocalDate date = last.plusDays(balance / dailyAverage);
        return date.isAfter(end) ? null : date;
    }
}
