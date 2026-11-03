package com.dothis.fintris.account.dto;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountStatus;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.entity.SupportedInstitution;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// connectedId와 전체 계좌번호는 응답에 담지 않는다
@Schema(description = "연동된 계좌 응답")
public record LinkedAccountResponse(
        @Schema(example = "1") Long linkedAccountId,
        @Schema(description = "CODEF 기관코드", example = "0004") String institutionCode,
        @Schema(example = "국민은행") String institutionName,
        @Schema(example = "BANK") LinkedAccountType type,
        @Schema(example = "KB국민ONE통장") String accountName,
        @Schema(description = "계좌번호 뒤 4자리", example = "1234") String accountNumberLast4,
        @Schema(description = "잔액(원). 카드는 null", example = "1250000") Long balance,
        @Schema(description = "미배분 잔액 계산 포함 여부", example = "true") boolean isActive,
        @Schema(example = "ACTIVE") LinkedAccountStatus status,
        @Schema(description = "마지막 동기화 시각. 아직 동기화 전이면 null") LocalDateTime lastSyncedAt
) {

    public static LinkedAccountResponse from(LinkedAccount account) {
        String institutionName = SupportedInstitution.findByCode(account.getInstitutionCode())
                .map(SupportedInstitution::getDisplayName)
                .orElse(account.getInstitutionCode());
        return new LinkedAccountResponse(
                account.getId(),
                account.getInstitutionCode(),
                institutionName,
                account.getType(),
                account.getAccountName(),
                account.getAccountNumberLast4(),
                account.getBalance(),
                account.isActive(),
                account.getStatus(),
                account.getLastSyncedAt()
        );
    }
}
