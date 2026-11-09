package com.dothis.fintris.block.simulation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// API 명세 v4 6장 이동·배분 미리보기 응답
@Schema(description = "이동·배분 미리보기 결과")
public record SimulateResponse(
        @Schema(description = "잔액이 바뀌는 블록") List<BlockChange> blocks,
        @Schema(description = "결과 항목 전체. danger → warn → good → info 순. FE는 3개까지 보여 준다") List<ImpactItem> items,
        @Schema(description = "3개 넘는 항목 수 ('나머지 n개 자세히')", example = "0") int hiddenCount,
        @Schema(description = "받는 블록이 모자란 금액 ([부족한 만큼] 버튼 값)") Shortfall shortfall,
        @Schema(description = "나눌 돈에서 배분할 때 남는 돈(원). 블록 간 이동이면 null", example = "null") Long remainingWaiting,
        @Schema(description = "danger 항목이 있어 한 번 더 확인해야 함", example = "false") boolean requiresConfirm,
        @Schema(description = "확정할 수 없음 (잔액보다 큰 금액 등)", example = "false") boolean blocked,
        @Schema(description = "확정할 수 없는 이유", example = "null") String blockedReason
) {

    @Schema(description = "블록 잔액 변화")
    public record BlockChange(
            @Schema(example = "3") Long blockId,
            @Schema(example = "여행") String name,
            @Schema(example = "320000") long balanceBefore,
            @Schema(example = "270000") long balanceAfter,
            @Schema(description = "확정 요청에 그대로 보낸다", example = "4") Long version
    ) {
    }

    public record Shortfall(
            @Schema(example = "1") Long blockId,
            @Schema(example = "0") long amount
    ) {
    }
}
