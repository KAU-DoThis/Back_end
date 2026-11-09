package com.dothis.fintris.block.dto;

import com.dothis.fintris.transaction.dto.TransactionResponse;
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
            @Schema(description = "최근부터 몇 달 연속 제때 냈는지. 납부 기록이 없으면 0", example = "6") int onTimeStreak,
            @Schema(description = "최근 납부 기록 (최대 6건, 최신순)") List<Payment> recentPayments
    ) {
    }

    @Schema(description = "고정지출 납부 기록 한 건")
    public record Payment(
            @Schema(example = "2026-09-25") LocalDate date,
            @Schema(example = "김*수") String payee,
            @Schema(description = "나간 계좌·카드", example = "국민은행 ••34") String account,
            @Schema(example = "600000") long amount,
            @Schema(description = "그달 납부일(없으면 말일)까지 냈으면 true", example = "true") boolean onTime
    ) {
    }

    @Schema(description = "사용형 상세 (02)")
    public record SpendingDetail(
            Period period,
            @Schema(description = "남은 비율(%) = 잔액 ÷ 월 배분액", example = "63") Integer remainingRate,
            @Schema(description = "기간 남은 일수(오늘 포함)", example = "18") long remainingDays,
            Today today,
            @Schema(description = "소진 속도. 월 배분액이 없으면 null") Burn burn,
            @Schema(description = "이 블록에 반영된 최근 거래 (최대 5건, 최신순)") List<TransactionResponse> recentTransactions
    ) {
    }

    @Schema(description = "소진 속도 (계획 대비 실제 누적)")
    public record Burn(
            @Schema(description = "오늘 기준 (실제 − 계획) ÷ 계획 × 100. 양수면 계획보다 빨리 씀", example = "17") Integer paceDiffRate,
            @Schema(example = "계획보다 17% 빠름") String paceLabel,
            @Schema(description = "기간 시작부터 오늘까지 쓴 돈", example = "550000") long actualSpent,
            @Schema(description = "기간 시작부터 오늘까지 하루 한 점") List<BurnPoint> series,
            @Schema(description = "지금 속도면 잔액이 바닥나는 날. 기간 안에 안 바닥나면 null", example = "2026-10-28")
            LocalDate expectedDepletionDate
    ) {
    }

    public record BurnPoint(
            @Schema(example = "2026-10-14") LocalDate date,
            @Schema(description = "그날까지 계획한 누적 지출", example = "677400") long planned,
            @Schema(description = "그날까지 실제 누적 지출", example = "550000") long actual
    ) {
    }

    public record Period(
            @Schema(example = "2026-10-01") LocalDate from,
            @Schema(example = "2026-10-31") LocalDate to
    ) {
    }

    public record Today(
            @Schema(description = "오늘 가능 (아침 기준 금액)", example = "52700") long available,
            @Schema(description = "오늘 이 블록에 반영된 거래 금액 합", example = "12300") long spent,
            @Schema(example = "52700") long remaining
    ) {
    }
}
