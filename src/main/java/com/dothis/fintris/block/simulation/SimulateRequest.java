package com.dothis.fintris.block.simulation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

// 블록 간 이동(03-2)과 나눌 돈 배분(06)이 같이 쓴다. 금액을 바꿀 때마다 FE가 다시 요청한다
@Schema(description = "이동·배분 미리보기 요청")
public record SimulateRequest(
        @NotEmpty List<@Valid @NotNull Change> changes
) {

    @Schema(description = "옮길 돈 하나. 보내는 쪽은 fromBlockId와 fromWaitingFundId 중 하나만")
    public record Change(
            @Schema(description = "보내는 블록", example = "3") Long fromBlockId,
            @Schema(description = "보내는 나눌 돈", example = "null") Long fromWaitingFundId,
            @Schema(description = "받는 블록", example = "1") @NotNull Long toBlockId,
            @Schema(description = "금액(원)", example = "50000") @NotNull @Positive Long amount
    ) {
    }
}
