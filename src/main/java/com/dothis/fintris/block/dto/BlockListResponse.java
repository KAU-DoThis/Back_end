package com.dothis.fintris.block.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "블록 목록")
public record BlockListResponse(
        @Schema(description = "displayOrder 순") List<BlockResponse> blocks
) {
}
