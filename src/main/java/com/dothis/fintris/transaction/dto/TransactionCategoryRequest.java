package com.dothis.fintris.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "거래 카테고리 수정 요청")
public record TransactionCategoryRequest(
        @Schema(example = "3") @NotNull Long categoryId
) {
}
