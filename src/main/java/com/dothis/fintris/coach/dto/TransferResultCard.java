package com.dothis.fintris.coach.dto;

import com.dothis.fintris.coach.entity.CoachProposal;
import com.dothis.fintris.coach.entity.ProposalStatus;
import com.dothis.fintris.coach.entity.TransferPreview;
import com.dothis.fintris.coach.entity.TransferResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// "50,000원 옮겼어요" 카드. 되돌린 결과도 같은 모양으로 방향만 반대로 보여 준다
@Schema(description = "이동 결과 카드")
public record TransferResultCard(
        @Schema(example = "TRANSFER_RESULT") String type,
        @Schema(example = "31") Long proposalId,
        @Schema(example = "34") Long transferId,
        @Schema(example = "50000") long amount,
        @Schema(description = "돈이 나간 블록") Side from,
        @Schema(description = "돈이 들어간 블록") Side to,
        @Schema(description = "되돌린 결과 카드면 true", example = "false") boolean undo,
        @Schema(description = "[되돌리기] 버튼을 보여 줄지", example = "true") boolean undoable,
        @Schema(description = "UNDO(되돌리기) · OPEN_HOME(홈에서 보기)", example = "[\"UNDO\", \"OPEN_HOME\"]") List<String> actions
) {

    @Schema(description = "옮긴 뒤 잔액과 바뀐 금액")
    public record Side(
            @Schema(example = "3") Long blockId,
            @Schema(example = "여행") String name,
            @Schema(example = "270000") long balanceAfter,
            @Schema(example = "-50000") long diff
    ) {
    }

    public static TransferResultCard transferred(CoachProposal proposal) {
        TransferPreview preview = proposal.getPreview();
        TransferResult result = proposal.getResult();
        boolean undoable = proposal.getStatus() == ProposalStatus.ACCEPTED;
        return new TransferResultCard("TRANSFER_RESULT", proposal.getId(), proposal.getTransferId(),
                proposal.getAmount(),
                new Side(preview.from().blockId(), preview.from().name(), result.fromBalanceAfter(), -proposal.getAmount()),
                new Side(preview.to().blockId(), preview.to().name(), result.toBalanceAfter(), proposal.getAmount()),
                false, undoable,
                undoable ? List.of("UNDO", "OPEN_HOME") : List.of("OPEN_HOME"));
    }

    // 되돌리기는 받았던 블록 → 보냈던 블록
    public static TransferResultCard undone(CoachProposal proposal) {
        TransferPreview preview = proposal.getPreview();
        TransferResult result = proposal.getUndoResult();
        return new TransferResultCard("TRANSFER_RESULT", proposal.getId(), proposal.getUndoTransferId(),
                proposal.getAmount(),
                new Side(preview.to().blockId(), preview.to().name(), result.fromBalanceAfter(), -proposal.getAmount()),
                new Side(preview.from().blockId(), preview.from().name(), result.toBalanceAfter(), proposal.getAmount()),
                true, false, List.of("OPEN_HOME"));
    }
}
