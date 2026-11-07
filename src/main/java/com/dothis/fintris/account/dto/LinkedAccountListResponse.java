package com.dothis.fintris.account.dto;

import com.dothis.fintris.account.entity.LinkedAccount;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Schema(description = "연동 목록 응답")
public record LinkedAccountListResponse(
        @Schema(description = "미배분 잔액 계산에 포함되는 계좌의 잔액 합(원)", example = "4577000") long totalBalance,
        @Schema(description = "가장 오래된 마지막 동기화 시각. 동기화한 계좌가 없으면 null") LocalDateTime lastSyncedAt,
        List<LinkedAccountResponse> linkedAccounts
) {

    public static LinkedAccountListResponse from(List<LinkedAccount> accounts) {
        long totalBalance = accounts.stream()
                .filter(LinkedAccount::isActive)
                .map(LinkedAccount::getBalance)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        // 홈 조회와 같은 기준으로, 가장 오래된 시각을 보여 준다
        LocalDateTime lastSyncedAt = accounts.stream()
                .map(LinkedAccount::getLastSyncedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        return new LinkedAccountListResponse(totalBalance, lastSyncedAt,
                accounts.stream().map(LinkedAccountResponse::from).toList());
    }
}
