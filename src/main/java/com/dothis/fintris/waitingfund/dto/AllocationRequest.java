package com.dothis.fintris.waitingfund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

// 나눌 돈 배분 실행 (앱 시안 06, 06-B, 06-C / API 명세 v4 6-1장)
@Schema(description = "나눌 돈 배분 요청")
public record AllocationRequest(
        @NotNull List<@Valid @NotNull Allocation> allocations,

        @Schema(description = "남은 돈 처리. KEEP: 나눌 돈에 남김(기본), DEFAULT_BLOCK: 생활비(기본 블록)에 넣기",
                example = "KEEP")
        LeftoverTo leftoverTo,

        // TODO: 정기 입금 자동 배분은 입금원 분석이 붙으면 반영한다. 지금은 받기만 한다
        @Schema(description = "다음 정기 입금부터 이대로 자동으로 나누기 (아직 저장하지 않음)", example = "false")
        Boolean autoAllocateNextTime
) {

    public record Allocation(
            @Schema(example = "4") @NotNull Long blockId,
            @Schema(example = "50000") @NotNull @Positive Long amount,
            @Schema(description = "미리보기에서 받은 블록 version. 바뀌었으면 409", example = "4") Long version
    ) {
    }

    public enum LeftoverTo {
        KEEP, DEFAULT_BLOCK
    }
}
