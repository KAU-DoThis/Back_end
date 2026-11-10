package com.dothis.fintris.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

// API 명세 v4 8장 소비 분석. 지난 기간 쓴 돈이 없으면(previousAvailable=false) 비교 값은 모두 null이다
@Schema(description = "소비 분석 응답")
public record SpendingAnalysisResponse(
        Period period,
        CompareTo compareTo,
        @Schema(example = "588000") long totalSpent,
        @Schema(example = "524000") Long previousSpent,
        @Schema(example = "64000") Long changeAmount,
        @Schema(description = "지난 기간 대비 증감률(%), 반올림", example = "12") Integer changeRate,
        @Schema(description = "쓴 돈이 많은 순. 분류 전 거래는 categoryId=null, code=UNCLASSIFIED") List<CategoryItem> categories,
        @Schema(description = "지난 기간보다 가장 많이 늘어난 카테고리(미분류 제외). 늘어난 곳이 없거나 비교할 수 없으면 null")
        BiggestIncrease biggestIncrease,
        @Schema(description = "반복 지출. REPEAT → DUPLICATE_SUBSCRIPTION → FIXED 순, 없으면 빈 목록") List<Recurring> recurring,
        boolean previousAvailable,
        @Schema(description = "기간 안에 쓴 돈이 없으면 true (빈 상태 화면)") boolean empty
) {

    public record Period(
            @Schema(example = "2026-10-01") LocalDate from,
            @Schema(description = "이번 달이면 오늘, 지난달이면 말일", example = "2026-10-14") LocalDate to
    ) {
    }

    public record CompareTo(
            @Schema(example = "2026-09-01") LocalDate from,
            @Schema(example = "2026-09-14") LocalDate to,
            @Schema(example = "9월 같은 기간") String label
    ) {
    }

    public record CategoryItem(
            @Schema(example = "2") Long categoryId,
            @Schema(example = "DELIVERY_DINING") String code,
            @Schema(example = "배달/외식") String name,
            @Schema(example = "13") long count,
            @Schema(example = "182000") long amount,
            @Schema(description = "지난 기간 같은 카테고리 대비 증감", example = "50000") Long changeAmount
    ) {
    }

    // TODO: explanation은 AI 설명 문장, excludedNote는 맥락 질문(F-15) 태그가 붙으면 채운다
    public record BiggestIncrease(
            @Schema(example = "2") Long categoryId,
            @Schema(example = "배달/외식") String name,
            @Schema(example = "50000") long changeAmount,
            @Schema(description = "지난 기간이 0원이면 null", example = "38") Integer changeRate,
            @Schema(example = "132000") long previousAmount,
            @Schema(example = "횟수가 9번에서 13번으로 늘었고, 한 번에 쓰는 돈은 비슷해요.") String explanation,
            List<TimeOfDay> timeOfDay,
            @Schema(example = "13번 중 9번이 밤 10시 이후였어요") String timeSummary,
            @Schema(example = "야근 식대로 표시한 2건(46,000원)은 절약 후보에서 뺐어요") String excludedNote
    ) {
    }

    public record Recurring(
            @Schema(description = "REPEAT · DUPLICATE_SUBSCRIPTION · FIXED", example = "REPEAT") String kind,
            @Schema(description = "REPEAT·DUPLICATE_SUBSCRIPTION은 카테고리 이름, FIXED는 받는 곳", example = "카페/간식") String label,
            @Schema(example = "15회 · 스타벅스 9회, 그 외 6회") String summary,
            @Schema(description = "FIXED는 최근 낸 금액, 나머지는 기간 안 합계", example = "76500") long amount
    ) {
    }

    public record TimeOfDay(
            @Schema(example = "NIGHT") String slot,
            @Schema(example = "밤 22시~") String label,
            @Schema(example = "9") long count
    ) {
    }
}
