package com.dothis.fintris.block.dto;

import com.dothis.fintris.block.entity.BlockRule;
import com.dothis.fintris.block.entity.BlockType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

// 04 구성안에서 금액을 고칠 때마다 보내는 미리보기. 확정 요청과 같은 모양이고 블록마다 proposalKey를 붙인다
// 확정 요청의 다른 필드(name, icon 등)를 같이 보내도 되고, 계산에는 아래 값만 쓴다
@Schema(description = "초기 블록 구성 미리보기 요청")
public record BlockProposalPreviewRequest(
        @NotEmpty List<@Valid @NotNull Item> blocks
) {

    @Schema(description = "미리보기할 블록")
    public record Item(
            @Schema(description = "구성안의 proposalKey. 응답에 그대로 돌려준다 (직접 추가한 블록은 FE가 정한 값)",
                    example = "spending-default") String proposalKey,
            @Schema(example = "SPENDING") @NotNull BlockType type,
            @Schema(description = "처음 넣을 금액(원)", example = "1200000") @NotNull @PositiveOrZero Long balance,
            @Schema(description = "사용형은 rule.monthlyAmount가 있으면 그 값으로 하루 금액을 계산한다") BlockRule rule,
            @Schema(description = "기본 사용형(생활비) 블록. 안 보내면 첫 사용형 블록", example = "true") Boolean isDefault
    ) {
    }
}
