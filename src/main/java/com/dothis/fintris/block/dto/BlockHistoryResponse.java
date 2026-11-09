package com.dothis.fintris.block.dto;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockTransfer;
import com.dothis.fintris.block.entity.TransferReason;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

// 블록 기록 (앱 시안 02-1 "들어온 돈", 02-2 "납부 기록 전체 보기" / API 명세 v4 6장)
@Schema(description = "블록 기록")
public record BlockHistoryResponse(
        List<Item> items,
        @Schema(example = "false") boolean hasNext
) {

    public enum Kind {
        ALLOCATION,   // 나눌 돈에서 배분
        TRANSFER_IN,  // 다른 블록에서 옮겨 옴
        TRANSFER_OUT, // 다른 블록이나 나눌 돈으로 옮김
        SAVING,       // 절약분 넣음
        PAYMENT,      // 고정지출 납부 (거래 반영 후)
        CREATED       // 블록 만들면서 첫 배분
    }

    @Schema(description = "기록 한 줄")
    public record Item(
            @Schema(example = "2026-10-01") LocalDate date,
            @Schema(example = "ALLOCATION") Kind kind,
            @Schema(example = "남은 돈에서 배분") String title,
            @Schema(example = "블록 구성 후 남은 돈") String subTitle,
            @Schema(description = "들어오면 +, 나가면 -", example = "120000") long amount
    ) {
        // 이 블록 입장에서 이동 하나를 한 줄로 읽는다
        public static Item of(Block block, BlockTransfer transfer) {
            LocalDate date = transfer.getCreatedAt().toLocalDate();
            long amount = transfer.getAmount();
            boolean incoming = transfer.getToBlock() != null && transfer.getToBlock().getId().equals(block.getId());
            if (!incoming) {
                if (transfer.getToBlock() != null) {
                    return new Item(date, Kind.TRANSFER_OUT, "'" + transfer.getToBlock().getName() + "' 블록으로 옮김",
                            null, -amount);
                }
                return new Item(date, Kind.TRANSFER_OUT, "나눌 돈으로 돌려보냄", null, -amount);
            }
            if (transfer.getFromBlock() != null) {
                return new Item(date, Kind.TRANSFER_IN, "'" + transfer.getFromBlock().getName() + "' 블록에서 옮겨 옴",
                        null, amount);
            }
            if (transfer.getFromWaitingFund() != null) {
                if (transfer.getReason() == TransferReason.SAVING) {
                    return new Item(date, Kind.SAVING, "절약분 넣음", transfer.getFromWaitingFund().getDescription(), amount);
                }
                return new Item(date, Kind.ALLOCATION, transfer.getFromWaitingFund().getLabel() + "에서 배분",
                        transfer.getFromWaitingFund().getDescription(), amount);
            }
            return new Item(date, Kind.CREATED, block.getName() + " 블록 만듦", "첫 배분", amount);
        }
    }
}
