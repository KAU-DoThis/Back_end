package com.dothis.fintris.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "수동 동기화 시작 결과")
public record SyncStartResponse(
        @Schema(description = "IN_PROGRESS(시작했거나 이미 진행 중) 또는 SKIPPED(1시간 이내라 건너뜀)", example = "IN_PROGRESS")
        SyncStatus syncStatus,
        @Schema(description = "가장 오래된 마지막 동기화 시각. 아직 없으면 null") LocalDateTime lastSyncedAt
) {
}
