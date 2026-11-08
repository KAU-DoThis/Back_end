package com.dothis.fintris.block.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

// 블록 상세 (앱 시안 02, 02-1, 02-2 / API 명세 v4 6장). detail은 유형별로 모양이 다르다
@Schema(description = "블록 상세")
public record BlockDetailResponse(
        BlockResponse block,
        // TODO: 상태 판정(F-11)이 붙으면 "왜 주의인가요?" 문장(AI, 실패 시 템플릿)을 채운다
        @Schema(description = "상태가 바뀐 이유. 상태 판정 전이라 지금은 null", example = "null") String statusReason,
        @Schema(description = "화면 버튼 (POSTPONE_DUE_DATE, PUT_WAITING_FUND, OPEN_SAVING_SUGGESTIONS, FILL_FROM_OTHER)")
        List<String> actions,
        @Schema(description = "규칙 한 줄", example = "목표 800,000 · 12월 31일 · 매달 200,000") String ruleText,
        @Schema(description = "유형별 상세: SpendingDetail · GoalDetail · FixedDetail",
                oneOf = {SpendingDetail.class, GoalDetail.class, FixedDetail.class})
        Object detail
) {

    @Schema(description = "목표형 상세 (02-1)")
    public record GoalDetail(
            @Schema(description = "달성률(%) = 잔액 ÷ 목표, 0~100", example = "40") int achievementRate,
            ThisMonth thisMonth,
            Projection projection,
            @Schema(description = "최근 들어온 돈 (최대 3건)") List<BlockHistoryResponse.Item> recentInflows,
            @Schema(description = "ACHIEVED(목표 달성 시트) · EXPIRED(기한 지남 시트) · null", example = "null")
            String resolutionNeeded
    ) {
    }

    @Schema(description = "이번 달 확보")
    public record ThisMonth(
            @Schema(description = "월 확보 금액", example = "200000") Long target,
            @Schema(description = "이번 달 모은 돈", example = "120000") long secured,
            @Schema(description = "더 필요한 돈", example = "80000") long needed
    ) {
    }

    @Schema(description = "언제 다 모일까요?")
    public record Projection(
            @Schema(description = "목표대로면 (기한)", example = "2026-12-31") LocalDate onTargetDate,
            @Schema(description = "지금 속도면 (월 확보 금액 기준). 정하지 않았으면 null", example = "2027-01-15") LocalDate currentPaceDate,
            @Schema(description = "기한보다 늦는 일수 (빠르면 음수)", example = "15") Long delayDays,
            @Schema(example = "목표보다 2주 늦음") String label,
            @Schema(description = "월별 누적 (실제 + 예상)") List<SeriesPoint> series
    ) {
    }

    public record SeriesPoint(
            @Schema(example = "2026-10") String month,
            @Schema(example = "320000") long cumulative,
            @Schema(description = "예상 구간이면 true", example = "false") boolean projected
    ) {
    }

    @Schema(description = "고정지출형 상세 (02-2)")
    public record FixedDetail(
            @Schema(description = "확보율(%) = 잔액 ÷ 납부액, 0~100", example = "100") int securedRate,
            @Schema(example = "2026-10-25") LocalDate nextPayDate,
            @Schema(example = "600000") long payAmount,
            @Schema(description = "모자란 돈", example = "0") long shortage,
            @Schema(description = "납부 확인 조건") Object matchCondition,
            @Schema(description = "알림 설정") Object alerts,
            // TODO: 거래 반영(F-21)이 붙으면 납부 기록과 제때 낸 횟수를 채운다
            @Schema(description = "몇 달 연속 제때 냈는지. 거래 반영 전이라 null", example = "null") Integer onTimeStreak,
            @Schema(description = "최근 납부 기록. 거래 반영 전이라 빈 배열") List<Object> recentPayments
    ) {
    }

    @Schema(description = "사용형 상세 (02)")
    public record SpendingDetail(
            Period period,
            @Schema(description = "남은 비율(%) = 잔액 ÷ 월 배분액", example = "63") Integer remainingRate,
            @Schema(description = "기간 남은 일수(오늘 포함)", example = "18") long remainingDays,
            Today today,
            // TODO: 거래 반영(F-21)이 붙으면 소진 속도 그래프와 최근 거래 5건을 채운다
            @Schema(description = "소진 속도. 거래 반영 전이라 null", example = "null") Object burn,
            @Schema(description = "최근 거래. 거래 반영 전이라 빈 배열") List<Object> recentTransactions
    ) {
    }

    public record Period(
            @Schema(example = "2026-10-01") LocalDate from,
            @Schema(example = "2026-10-31") LocalDate to
    ) {
    }

    public record Today(
            @Schema(description = "오늘 가능 (아침 기준 금액)", example = "52700") long available,
            @Schema(description = "오늘 쓴 돈. 거래 반영 전이라 0", example = "0") long spent,
            @Schema(example = "52700") long remaining
    ) {
    }
}
