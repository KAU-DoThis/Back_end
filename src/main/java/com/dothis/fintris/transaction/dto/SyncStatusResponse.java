package com.dothis.fintris.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "동기화 상태")
public record SyncStatusResponse(
        @Schema(example = "DONE") SyncStatus syncStatus,
        @Schema(description = "가장 오래된 마지막 동기화 시각. 아직 없으면 null") LocalDateTime lastSyncedAt,
        @Schema(description = "마지막 동기화에서 실패한 계좌 수", example = "0") int failedCount
) {
}
