package com.dothis.fintris.notification.dto;

import com.dothis.fintris.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// API 명세 v4 9장 이상 징후 상세 (13-1 생활비 소진 속도). 금액은 모두 서버 계산
@Schema(description = "소진 속도 경고 상세")
public record BurnRateDetailResponse(
        @Schema(example = "301") Long notificationId,
        @Schema(example = "BURN_RATE") NotificationType type,
        @Schema(example = "false") boolean urgent,
        Block block,
        @Schema(description = "평소를 모르거나 평소보다 느리면 최근 하루 평균만 말한다", example = "평소보다 17% 빨리 줄고 있어요")
        String headline,
        Recent7Days recent7Days,
        @Schema(description = "기간 안에 바닥나지 않으면 null") Depletion depletion,
        @Schema(description = "안전하려면 오늘부터 하루 이만큼만. 남은 잔액 ÷ 남은 일수(오늘 포함), 100원 내림", example = "52700")
        long safeDaily,
        @Schema(description = "평소보다 많이 쓴 카테고리. 더 쓴 금액 순, 최대 3개") List<TopIncrease> topIncreases,
        @Schema(description = "FILL_FROM_OTHER(다른 블록에서 채우기) · CREATE_SAVING_GOAL(절약 목표 만들기)")
        List<String> actions,
        @Schema(example = "2026-10-14T09:00:00") LocalDateTime calculatedAt
) {

    public record Block(
            @Schema(example = "1") Long blockId,
            @Schema(example = "생활비") String name,
            @Schema(example = "950000") long balance,
            @Schema(description = "기간 끝까지 남은 일수(오늘 포함)", example = "18") long remainingDays
    ) {
    }

    @Schema(description = "막대 그래프. 평소 하루 평균은 가로선, aboveUsual이면 진하게")
    public record Recent7Days(
            @Schema(example = "68000") long dailyAvg,
            @Schema(description = "직전 4주 하루 평균. 기록이 없으면 null", example = "58000") Long usualDailyAvg,
            List<DayAmount> series
    ) {
    }

    public record DayAmount(
            @Schema(example = "2026-10-14") LocalDate date,
            @Schema(example = "91000") long amount,
            @Schema(example = "true") boolean aboveUsual
    ) {
    }

    public record Depletion(
            @Schema(description = "이 속도면 바닥나는 날", example = "2026-10-28") LocalDate date,
            @Schema(description = "바닥난 뒤 기간 끝까지 남는 날 수", example = "3") long shortDays
    ) {
    }

    public record TopIncrease(
            @Schema(example = "카페/간식") String label,
            @Schema(example = "6건") String summary,
            @Schema(description = "평소 일주일보다 더 쓴 금액", example = "18000") long extraAmount
    ) {
    }
}
