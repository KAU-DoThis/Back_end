package com.dothis.fintris.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "빠른 입력 저장 요청 (미리보기 결과를 사용자가 고친 값)")
public record ManualSaveRequest(
        @Schema(example = "3000") @NotNull @Positive @Max(100_000_000L) Long amount,
        @Schema(example = "붕어빵") @Size(max = 255) String description,
        @Schema(example = "2026-10-14") @NotNull @PastOrPresent LocalDate occurredDate,
        @Schema(description = "카테고리. 비우면 기본 생활비 블록에서 뺀다", example = "3") Long categoryId,
        @Schema(description = "같은 금액 ATM 출금과 겹칠 때 건너뛸 거래 ID. 아직 쓰지 않는다", example = "null")
        Long skipAtmTransactionId
) {
}
