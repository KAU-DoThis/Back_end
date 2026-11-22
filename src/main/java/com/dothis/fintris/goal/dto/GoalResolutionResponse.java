package com.dothis.fintris.goal.dto;

import com.dothis.fintris.block.dto.BlockResponse;
import com.dothis.fintris.block.dto.TransferResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "목표 처리 결과")
public record GoalResolutionResponse(
        @Schema(description = "처리 후 목표 블록. CLEANUP으로 삭제했으면 null") BlockResponse block,
        @Schema(description = "MOVE·CLEANUP으로 돈을 받은 블록") List<TransferResponse.BlockBalance> movedBlocks
) {
}
