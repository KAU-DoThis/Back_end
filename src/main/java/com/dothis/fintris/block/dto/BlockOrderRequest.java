package com.dothis.fintris.block.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "블록 순서 변경 요청")
public record BlockOrderRequest(
        @Schema(description = "원하는 순서대로 나열한 블록 ID. 삭제되지 않은 내 블록 전체를 보내야 한다",
                example = "[3, 1, 2]")
        @NotEmpty List<@NotNull Long> blockIds
) {
}
