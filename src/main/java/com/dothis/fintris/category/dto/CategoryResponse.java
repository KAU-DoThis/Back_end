package com.dothis.fintris.category.dto;

import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카테고리")
public record CategoryResponse(
        @Schema(example = "3") Long categoryId,
        @Schema(example = "CAFE") String code,
        @Schema(example = "카페/간식") String name,
        @Schema(description = "EXPENSE 소비 · TRANSFER 이체 유형", example = "EXPENSE") CategoryType type
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getCode(), category.getName(), category.getCategoryType());
    }
}
