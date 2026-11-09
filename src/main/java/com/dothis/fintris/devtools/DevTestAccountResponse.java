package com.dothis.fintris.devtools;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "개발용 테스트 계좌")
public record DevTestAccountResponse(
        @Schema(example = "12") Long linkedAccountId,
        @Schema(description = "잔액(원)", example = "3000000") long balance,
        @Schema(description = "이번 요청으로 새로 만들었으면 true, 있던 계좌 잔액만 바꿨으면 false", example = "true")
        boolean created
) {
}
