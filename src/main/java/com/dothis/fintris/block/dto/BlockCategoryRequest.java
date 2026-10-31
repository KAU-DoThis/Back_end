package com.dothis.fintris.block.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "블록 카테고리 연결 설정 요청")
public record BlockCategoryRequest(
        @Schema(description = "연결할 카테고리 ID 전체. 빈 배열이면 연결을 모두 해제한다", example = "[1, 2]")
        @NotNull List<@NotNull Long> categoryIds
) {
}
