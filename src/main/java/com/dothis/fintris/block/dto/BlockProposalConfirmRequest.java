package com.dothis.fintris.block.dto;

import com.dothis.fintris.block.entity.BlockColor;
import com.dothis.fintris.block.entity.BlockCreatedBy;
import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

// 사용자가 지우거나 고친 구성안 최종본. 보낸 순서가 displayOrder가 된다 (API 명세 v4 6장)
@Schema(description = "초기 블록 확정 요청")
public record BlockProposalConfirmRequest(
        @NotEmpty List<@Valid @NotNull Item> blocks
) {

    @Schema(description = "확정할 블록")
    public record Item(
            @Schema(example = "FIXED") @NotNull BlockType type,
            @Schema(example = "월세") @NotBlank @Size(max = 50) String name,
            @Schema(example = "home") @Size(max = 50) String icon,
            @Schema(example = "L") BlockColor color,
            @Schema(description = "처음 넣을 금액(원)", example = "600000") @NotNull @PositiveOrZero Long balance,
            @Schema(description = "목표형 목표 금액", example = "null") Long targetAmount,
            @Schema(description = "사용형=기간 끝(없으면 이번 달 말일), 목표형=기한", example = "null") LocalDate dueDate,
            BlockRule rule,
            @Schema(description = "기본 사용형(생활비) 블록. 안 보내면 첫 사용형 블록이 기본이 된다", example = "false")
            Boolean isDefault,
            @Schema(description = "AI 구성안에서 왔으면 AI", example = "AI") BlockCreatedBy createdBy
    ) {
        public BlockCreateRequest toCreateRequest() {
            return new BlockCreateRequest(type, name, icon, color, targetAmount, null, dueDate, rule);
        }
    }
}
