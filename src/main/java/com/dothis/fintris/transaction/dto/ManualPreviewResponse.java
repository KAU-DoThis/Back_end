package com.dothis.fintris.transaction.dto;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.category.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

// 빠른 입력 미리보기 (10). 저장 전에 사용자가 고칠 수 있게 해석 결과와 차감될 블록을 보여 준다
@Schema(description = "빠른 입력 미리보기")
public record ManualPreviewResponse(
        @Schema(example = "3000") long amount,
        @Schema(description = "내용. 금액만 입력했으면 null", example = "붕어빵") String description,
        @Schema(example = "2026-10-14") LocalDate occurredDate,
        @Schema(description = "카테고리. AI 해석 전이라 지금은 null") TransactionResponse.CategoryItem category,
        @Schema(description = "차감될 블록. 고를 블록이 없으면 null") BlockItem block,
        // TODO: 같은 금액 ATM 출금이 있으면 이중 차감인지 묻는다 (명세 4장)
        @Schema(description = "같은 금액 ATM 출금. 아직 확인하지 않아 null", example = "null") Object possibleAtmDuplicate
) {

    public static ManualPreviewResponse of(long amount, String description, LocalDate occurredDate,
                                           Category category, Block block) {
        return new ManualPreviewResponse(amount, description, occurredDate,
                TransactionResponse.CategoryItem.from(category), BlockItem.from(block), null);
    }

    public record BlockItem(
            @Schema(example = "1") Long blockId,
            @Schema(example = "생활비") String name,
            @Schema(example = "true") boolean isDefault
    ) {
        static BlockItem from(Block block) {
            return block == null ? null : new BlockItem(block.getId(), block.getName(), block.isDefault());
        }
    }
}
