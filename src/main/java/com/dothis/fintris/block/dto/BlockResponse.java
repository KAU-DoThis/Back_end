package com.dothis.fintris.block.dto;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockCategory;
import com.dothis.fintris.block.entity.BlockCreatedBy;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockStatus;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.category.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "블록 응답")
public record BlockResponse(
        @Schema(example = "1") Long blockId,
        @Schema(example = "GOAL") BlockType type,
        @Schema(example = "제주 여행") String name,
        @Schema(example = "airplane") String icon,
        @Schema(description = "잔액(원)", example = "0") long balance,
        @Schema(description = "기본 블록 여부(삭제 불가)", example = "false") boolean isDefault,
        @Schema(description = "목표 금액(원)", example = "600000") Long targetAmount,
        @Schema(example = "2026-10-20") LocalDate startDate,
        @Schema(example = "2027-02-28") LocalDate dueDate,
        BlockRule rule,
        @Schema(example = "SAFE") BlockStatus status,
        @Schema(example = "USER") BlockCreatedBy createdBy,
        @Schema(example = "0") int displayOrder,
        List<CategoryItem> categories
) {

    @Schema(description = "연결된 카테고리")
    public record CategoryItem(
            @Schema(example = "1") Long categoryId,
            @Schema(example = "FOOD") String code,
            @Schema(example = "식비") String name
    ) {
        static CategoryItem from(Category category) {
            return new CategoryItem(category.getId(), category.getCode(), category.getName());
        }
    }

    public static BlockResponse of(Block block, List<BlockCategory> blockCategories) {
        return new BlockResponse(
                block.getId(),
                block.getType(),
                block.getName(),
                block.getIcon(),
                block.getBalance(),
                block.isDefault(),
                block.getTargetAmount(),
                block.getStartDate(),
                block.getDueDate(),
                block.getRule(),
                block.getStatus(),
                block.getCreatedBy(),
                block.getDisplayOrder(),
                blockCategories.stream()
                        .map(bc -> CategoryItem.from(bc.getCategory()))
                        .toList()
        );
    }
}
