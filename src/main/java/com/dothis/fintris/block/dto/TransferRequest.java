package com.dothis.fintris.block.dto;

import com.dothis.fintris.block.entity.TransferReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 블록 간 이동 확정 (앱 시안 03-2 [옮기기], API 명세 v4 6장)
@Schema(description = "블록 간 이동 요청")
public record TransferRequest(
        @Schema(example = "3") @NotNull Long fromBlockId,
        @Schema(example = "1") @NotNull Long toBlockId,
        @Schema(description = "금액(원)", example = "50000") @NotNull @Positive Long amount,
        @Schema(description = "MANUAL · SUGGESTION · AI. 없으면 MANUAL", example = "MANUAL") TransferReason reason,
        @Schema(description = "미리보기에서 받은 보내는 블록 version. 바뀌었으면 409", example = "4") Long fromVersion,
        @Schema(description = "미리보기에서 받은 받는 블록 version. 바뀌었으면 409", example = "7") Long toVersion,
        @Schema(description = "requiresConfirm이었던 이동을 한 번 더 확인했는지", example = "true") Boolean confirmed
) {
}
