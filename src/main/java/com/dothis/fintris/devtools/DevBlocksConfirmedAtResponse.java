package com.dothis.fintris.devtools;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "블록 확정 시각 옮기기 결과")
public record DevBlocksConfirmedAtResponse(
        @Schema(example = "2026-11-01T00:00:00") LocalDateTime confirmedAt,
        @Schema(description = "다시 맞춘 출금 수 (블록에서 새로 빼거나 되돌린 거래)", example = "35") int reconciled,
        @Schema(description = "블록 충돌로 건너뛴 출금 수. 0이 아니면 한 번 더 호출", example = "0") int skipped
) {
}
