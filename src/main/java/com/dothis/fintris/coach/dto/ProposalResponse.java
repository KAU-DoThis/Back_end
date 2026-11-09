package com.dothis.fintris.coach.dto;

import com.dothis.fintris.coach.entity.CoachProposal;
import com.dothis.fintris.coach.entity.ProposalStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// 제안 하나의 지금 상태. 대화 기록(AI 서버)에 남은 proposalId로 카드를 다시 그릴 때 쓴다
@Schema(description = "제안 상태와 카드")
public record ProposalResponse(
        @Schema(example = "31") Long proposalId,
        @Schema(example = "ACCEPTED") ProposalStatus status,
        @Schema(description = "제안 카드 (옮기기 전 숫자)") TransferProposalCard proposal,
        @Schema(description = "옮긴 결과 카드. 수락 전이면 null") TransferResultCard result,
        @Schema(description = "되돌린 결과 카드. 되돌리지 않았으면 null") TransferResultCard undoResult
) {

    public static ProposalResponse of(CoachProposal proposal, LocalDateTime now) {
        return new ProposalResponse(
                proposal.getId(),
                proposal.statusAt(now),
                TransferProposalCard.of(proposal, now),
                proposal.getResult() != null ? TransferResultCard.transferred(proposal) : null,
                proposal.getUndoResult() != null ? TransferResultCard.undone(proposal) : null);
    }
}
