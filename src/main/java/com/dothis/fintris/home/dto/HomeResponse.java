package com.dothis.fintris.home.dto;

import com.dothis.fintris.block.dto.BlockResponse;
import com.dothis.fintris.connection.entity.LinkedAccount;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// API 명세 v4 5장. 목표는 목표형 블록 자체라 goalId는 따로 두지 않는다 (blocks[].blockId, type=GOAL)
@Schema(description = "홈 화면 응답")
public record HomeResponse(
        @Schema(description = "잔액 기준일. 계좌 중 가장 오래 전에 갱신된 날, 계좌가 없으면 오늘", example = "2026-10-14")
        LocalDate asOf,

        @Schema(description = "연동 은행 계좌 잔액 합(원)", example = "4577000")
        long totalBalance,

        @Schema(description = "연동 은행 계좌 수", example = "2")
        int linkedAccountCount,

        @Schema(description = "오늘 쓸 수 있는 돈. 기본 블록이 없거나 기간이 지났으면 null")
        DailyGuide dailyGuide,

        @Schema(description = "홈 상단 카드를 넘기면 나오는 계좌별 카드 (01-1)")
        List<AccountItem> accounts,

        // TODO: 제안 카드 규칙(API 명세 v4 0장 SuggestionCard)이 붙으면 채운다
        @Schema(description = "제안 배너. 없으면 null")
        Object suggestion,

        // TODO: 나눌 돈(#16)이 붙으면 대기 자금 목록으로 채운다
        @Schema(description = "나눌 돈(대기 자금) 목록. 금액이 0보다 큰 것만, 비어 있으면 영역을 숨긴다")
        List<Object> waitingFunds,

        @Schema(description = "블록 합계 + 나눌 돈 > 실제 잔액이면 true (경고 + 줄일 블록 선택)", example = "false")
        boolean overAllocated,

        @Schema(description = "블록 타일 목록. displayOrder 순, 응답 형식은 GET /blocks와 같다")
        List<BlockResponse> blocks,

        @Schema(description = "마지막 동기화 시각. 계좌 중 가장 오래된 값, 계좌가 없으면 null", example = "2026-10-14T09:00:00")
        LocalDateTime lastSyncedAt,

        // TODO: 알림함(F-22)이 붙으면 읽지 않은 알림 수로 바꾼다
        @Schema(description = "읽지 않은 알림 수", example = "0")
        int unreadNotificationCount,

        // TODO: 맥락 질문(F-15)이 붙으면 답하지 않은 질문 수로 바꾼다
        @Schema(description = "답하지 않은 맥락 질문 수", example = "0")
        int pendingQuestionCount
) {

    @Schema(description = "오늘 쓸 수 있는 돈 (F-10)")
    public record DailyGuide(
            @Schema(description = "기본 사용형 블록 ID", example = "1") Long blockId,
            @Schema(example = "생활비") String blockName,
            @Schema(description = "기본 블록 잔액(원)", example = "950000") long blockBalance,
            @Schema(description = "아침 기준 금액 = 잔액 ÷ 남은 일수, 100원 단위 내림(원)", example = "52700") long todayAvailable,
            @Schema(description = "오늘 쓴 돈(원)", example = "12300") long todaySpent,
            @Schema(description = "오늘 남은 금액 = todayAvailable - todaySpent(원)", example = "40400") long todayRemaining,
            @Schema(description = "기간 남은 일수(오늘 포함)", example = "18") long remainingDays,
            @Schema(description = "BLOCK_PERIOD: 기본 블록 기간 기준 / NEXT_INCOME: 다음 입금까지 기준(설계 중)",
                    example = "BLOCK_PERIOD") String basis
    ) {
    }

    @Schema(description = "계좌별 카드")
    public record AccountItem(
            @Schema(example = "3") Long linkedAccountId,
            @Schema(description = "금융기관 코드", example = "0004") String institutionCode,
            @Schema(description = "금융기관 이름. 기관 목록(F-19)이 붙기 전까지 null", example = "KB국민") String institutionName,
            @Schema(example = "입출금") String accountName,
            @Schema(description = "계좌번호 뒤 4자리", example = "1234") String last4,
            @Schema(description = "잔액(원)", example = "2687000") Long balance,
            @Schema(description = "이번 달 이 계좌에서 쓴 돈(원)", example = "115900") long monthSpent
    ) {
        // TODO: institutionName은 기관 목록(F-19), monthSpent는 거래(F-21)가 붙으면 채운다
        public static AccountItem from(LinkedAccount account) {
            return new AccountItem(
                    account.getId(),
                    account.getInstitutionCode(),
                    null,
                    account.getAccountName(),
                    account.getAccountNumberLast4(),
                    account.getBalance(),
                    0
            );
        }
    }
}
