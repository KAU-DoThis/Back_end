package com.dothis.fintris.block.dto;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockCategory;
import com.dothis.fintris.block.entity.BlockColor;
import com.dothis.fintris.block.entity.BlockCreatedBy;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockStatus;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.service.BlockCalculator;
import com.dothis.fintris.category.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

// API 명세 v4 0장 블록 객체
@Schema(description = "블록 객체")
public record BlockResponse(
        @Schema(example = "1") Long blockId,
        @Schema(example = "SPENDING") BlockType type,
        @Schema(example = "생활비") String name,
        @Schema(example = "wallet") String icon,
        @Schema(description = "블록 색 코드 I·O·T·S·Z·J·L", example = "I") BlockColor color,
        @Schema(description = "잔액(원)", example = "950000") long balance,
        @Schema(description = "비커 크기 기준 금액. 사용형=월 배분액, 목표형=목표 금액, 고정지출형=납부액. 정하지 않았으면 null",
                example = "1500000") Long capacity,
        @Schema(description = "물 높이(%) = 잔액 ÷ capacity, 0~100", example = "63") Integer fillRate,
        @Schema(description = "기본 블록 여부(삭제 불가)", example = "true") boolean isDefault,
        @Schema(description = "목표 금액(원). 목표형만", example = "null") Long targetAmount,
        @Schema(example = "2026-10-01") LocalDate startDate,
        @Schema(example = "2026-10-31") LocalDate dueDate,
        @Schema(description = "고정지출형=다음 납부일까지, 목표형=목표일까지 남은 일수. 사용형 null", example = "null") Long dDay,
        BlockRule rule,
        @Schema(example = "CAUTION") BlockStatus status,
        @Schema(description = "상태 라벨", example = "주의") String statusLabel,
        @Schema(description = "상태 칩 옆 짧은 이유. 없으면 null", example = "목표 달성") String statusSubLabel,
        @Schema(example = "2") int displayOrder,
        @Schema(example = "AI") BlockCreatedBy createdBy,
        @Schema(description = "낙관적 잠금 버전. 수정·이동 요청에 그대로 보낸다", example = "7") Long version,
        List<CategoryItem> categories
) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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
        Long capacity = BlockCalculator.capacity(block);
        return new BlockResponse(
                block.getId(),
                block.getType(),
                block.getName(),
                block.getIcon(),
                block.colorOrDefault(),
                block.getBalance(),
                capacity,
                BlockCalculator.fillRate(block.getBalance(), capacity),
                block.isDefault(),
                block.getTargetAmount(),
                block.getStartDate(),
                block.getDueDate(),
                BlockCalculator.dDay(block, LocalDate.now(KST)),
                block.getRule(),
                block.getStatus(),
                block.getStatus().getLabel(),
                BlockCalculator.statusSubLabel(block),
                block.getDisplayOrder(),
                block.getCreatedBy(),
                block.getVersion(),
                blockCategories.stream()
                        .map(bc -> CategoryItem.from(bc.getCategory()))
                        .toList()
        );
    }
}
