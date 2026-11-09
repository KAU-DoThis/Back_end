package com.dothis.fintris.devtools;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "개발용 테스트 계좌 잔액 설정")
public record DevTestAccountRequest(
        @Schema(description = "테스트 계좌 잔액(원)", example = "3000000")
        @NotNull @Min(0) @Max(1_000_000_000L) Long balance
) {
}
