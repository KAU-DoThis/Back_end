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
}
