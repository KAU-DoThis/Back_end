package com.dothis.fintris.block.dto;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "블록 간 이동 결과")
public record TransferResponse(
        @Schema(example = "34") Long transferId,
        BlockBalance fromBlock,
        BlockBalance toBlock,
        @Schema(description = "기본 사용형 블록 기준 오늘 쓸 수 있는 돈. 기본 블록이 없거나 기간이 끝났으면 null") DailyGuide dailyGuide,
        // TODO: 되돌리기 예약(autoReturn)은 입금원 분석이 붙으면 추가한다
        @Schema(description = "다음 정기 입금 때 되돌리기 예약. 아직 지원하지 않아 null", example = "null") Object scheduledReturn
) {

    @Schema(description = "이동 후 블록")
    public record BlockBalance(
            @Schema(example = "3") Long blockId,
            @Schema(example = "270000") long balance,
            @Schema(example = "SAFE") BlockStatus status,
            @Schema(description = "다음 수정·이동 요청에 보낸다", example = "5") Long version
    ) {
        public static BlockBalance from(Block block) {
            return new BlockBalance(block.getId(), block.getBalance(), block.getStatus(), block.getVersion());
        }
    }

    public record DailyGuide(
            @Schema(example = "55500") long todayAvailable,
            @Schema(description = "오늘 쓴 돈은 거래 반영(F-21) 전까지 0이라 todayAvailable과 같다", example = "55500")
            long todayRemaining
    ) {
    }
}
