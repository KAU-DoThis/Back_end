package com.dothis.fintris.home.dto;

import com.dothis.fintris.connection.entity.LinkedAccount;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

// 블록 타일 목록은 GET /blocks를 쓴다
@Schema(description = "홈 화면 응답")
public record HomeResponse(
        @Schema(description = "연동 은행 계좌 잔액 합(원)", example = "4577000")
        long totalAssets,

        @Schema(description = "연동 은행 계좌 수", example = "2")
        int linkedAccountCount,

        @Schema(description = "잔액 기준 시각. 계좌 중 가장 오래 전에 갱신된 시각, 계좌가 없으면 null",
                example = "2026-10-14T09:00:00")
        LocalDateTime balanceUpdatedAt,

        @Schema(description = "미배분 잔액(원) = 총 자산 - 블록 잔액 합. 0 미만이면 블록이 실제 잔액보다 크다",
                example = "207000")
        long unallocatedAmount,

        @Schema(description = "오늘 쓸 수 있는 돈. 기본 블록이 없거나 기간이 지났으면 null")
        DailyGuide dailyGuide,

        @Schema(description = "은행 카드 넘김용 계좌 목록")
        List<AccountItem> accounts
) {

    @Schema(description = "오늘 쓸 수 있는 돈 (F-10)")
    public record DailyGuide(
            @Schema(description = "기본 사용형 블록 ID", example = "2") Long blockId,
            @Schema(example = "생활비") String blockName,
            @Schema(description = "기본 블록 잔액(원)", example = "950000") long balance,
            @Schema(description = "기간 남은 일수(오늘 포함)", example = "18") long remainingDays,
            @Schema(description = "잔액 ÷ 남은 일수, 100원 단위 내림(원)", example = "52700") long todayAvailable
    ) {
    }

    @Schema(description = "연동 계좌")
    public record AccountItem(
            @Schema(example = "1") Long linkedAccountId,
            @Schema(description = "금융기관 코드", example = "004") String institutionCode,
            @Schema(example = "KB국민 입출금") String accountName,
            @Schema(description = "계좌번호 뒤 4자리", example = "1234") String accountNumberLast4,
            @Schema(description = "잔액(원)", example = "3000000") Long balance,
            @Schema(example = "2026-10-14T09:00:00") LocalDateTime balanceUpdatedAt
    ) {
        public static AccountItem from(LinkedAccount account) {
            return new AccountItem(
                    account.getId(),
                    account.getInstitutionCode(),
                    account.getAccountName(),
                    account.getAccountNumberLast4(),
                    account.getBalance(),
                    account.getBalanceUpdatedAt()
            );
        }
    }
}
