package com.dothis.fintris.devtools;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "블록 확정 시각 옮기기 (개발 서버 시연용)")
public record DevBlocksConfirmedAtRequest(
        @Schema(description = "옮길 확정 시각(KST). 이 시각 뒤 출금이 블록에서 빠진다", example = "2026-11-01T00:00:00")
        @NotNull LocalDateTime confirmedAt
) {
}
