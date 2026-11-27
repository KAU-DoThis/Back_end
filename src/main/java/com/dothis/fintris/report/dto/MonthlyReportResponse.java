package com.dothis.fintris.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

// 08 월간 리포트. 숫자는 모두 서버가 계산하고, 한 달 정리 문장만 템플릿(나중에 AI)이다
@Schema(description = "월간 리포트 (08)")
public record MonthlyReportResponse(
        @Schema(example = "2026-09") String month,
        @Schema(description = "집계한 기간. 이번 달이면 오늘까지") Period period,
        @Schema(description = "이번 달 리포트(아직 진행 중)인지", example = "false") boolean inProgress,
        Score score,
        Spending spending,
        Saving saving,
        @Schema(description = "주요 소비 변화. 지난달 대비 차이가 큰 순서로 최대 3개") List<CategoryChange> categoryChanges,
        Comment comment
) {

    public record Period(@Schema(example = "2026-09-01") LocalDate from, @Schema(example = "2026-09-30") LocalDate to) {
    }

    @Schema(description = "금융관리 점수 (F-14)")
    public record Score(
            @Schema(description = "점수 0~100. 점수를 낼 수 없으면 null (note에 이유)", example = "78") Integer total,
            @Schema(description = "지난달 점수. 없으면 null", example = "72") Integer previousTotal,
            @Schema(description = "지난달 대비. 둘 중 하나라도 없으면 null", example = "6") Integer change,
            @Schema(description = "점수를 낼 수 없을 때 이유", example = "null") String note,
            List<ScoreItem> items
    ) {
    }

    @Schema(description = "점수 항목")
    public record ScoreItem(
            @Schema(description = "FIXED·PACE·SAVING·GOAL", example = "SAVING") String key,
            @Schema(example = "절약 실천") String label,
            @Schema(description = "배점", example = "15") int weight,
            @Schema(description = "달성률(%). 해당 없으면 null이고 배점은 다른 항목에 나눈다", example = "89") Integer rate
    ) {
    }

    @Schema(description = "총 지출과 날짜별 누적 지출 (더치페이는 내 몫 기준)")
    public record Spending(
            @Schema(example = "2140000") long total,
            @Schema(description = "지난달 같은 기간", example = "2230000") long previousTotal,
            @Schema(example = "-90000") long changeAmount,
            @Schema(description = "지난달 대비 %, 반올림. 지난달이 0원이면 null", example = "-4") Integer changeRate,
            @Schema(description = "날짜별 누적 지출 그래프") List<DailyPoint> daily
    ) {
    }

    @Schema(description = "그날까지 누적 지출. 그 달에 없는 날(예: 9월 31일)이나 아직 안 온 날은 null")
    public record DailyPoint(
            @Schema(example = "15") int day,
            @Schema(example = "1080000") Long cumulative,
            @Schema(example = "1120000") Long previousCumulative
    ) {
    }

    @Schema(description = "그 달에 확정된 절약 목표와 절약분 배분")
    public record Saving(
            @Schema(description = "확정 절약액 합", example = "87000") long confirmed,
            @Schema(description = "예상 절약액 합 (화면의 '목표')", example = "98000") long expected,
            @Schema(description = "절약분에서 블록으로 나눈 금액 합", example = "87000") long allocated,
            List<AllocatedBlock> allocatedBlocks
    ) {
    }

    public record AllocatedBlock(@Schema(example = "4") Long blockId, @Schema(example = "여행") String name,
                                 @Schema(example = "50000") long amount) {
    }

    public record CategoryChange(
            @Schema(description = "분류 전 거래는 null", example = "2") Long categoryId,
            @Schema(example = "배달") String name,
            @Schema(example = "172000") long previous,
            @Schema(example = "120000") long current,
            @Schema(example = "-52000") long change
    ) {
    }

    public record Comment(
            @Schema(example = "9월에는 2,140,000원을 썼어요. 지난달보다 90,000원 줄었어요.") String text,
            @Schema(description = "TEMPLATE(서버 문장) 또는 AI", example = "TEMPLATE") String source
    ) {
    }
}
