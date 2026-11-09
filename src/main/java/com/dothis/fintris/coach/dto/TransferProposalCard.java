package com.dothis.fintris.coach.dto;

import com.dothis.fintris.coach.entity.CoachProposal;
import com.dothis.fintris.coach.entity.ProposalStatus;
import com.dothis.fintris.coach.entity.TransferPreview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "이렇게 옮길까요?" 카드. 아직 옮기지 않은 상태의 숫자
@Schema(description = "이동 제안 카드")
public record TransferProposalCard(
        @Schema(example = "TRANSFER_PROPOSAL") String type,
        @Schema(example = "31") Long proposalId,
        @Schema(description = "PENDING이 아니면 FE는 수락·거절 버튼을 숨긴다", example = "PENDING") ProposalStatus status,
        @Schema(example = "50000") long amount,
        @Schema(description = "보내는 블록 (옮기기 전·후 잔액)") TransferPreview.Side from,
        @Schema(description = "받는 블록 (옮기기 전·후 잔액)") TransferPreview.Side to,
        @Schema(description = "목표형 예상 달성일 변화. 없으면 null") TransferPreview.GoalDelay goalDelay,
        @Schema(description = "기본 사용형 블록 기준 하루 쓸 돈 변화. 없으면 null") TransferPreview.DailyBudget dailyBudget,
        @Schema(description = "고정지출형 블록 납부 영향") TransferPreview.FixedImpact fixedImpact,
        @Schema(description = "고정지출이 모자라게 되는 등, 수락 전에 한 번 더 확인", example = "false") boolean requiresConfirm,
        @Schema(description = "잔액보다 큰 금액 등으로 수락할 수 없음", example = "false") boolean blocked,
        @Schema(example = "null") String blockedReason,
        @Schema(example = "2026-11-09T14:12:01") LocalDateTime expiresAt
) {

    public static TransferProposalCard of(CoachProposal proposal, LocalDateTime now) {
        TransferPreview preview = proposal.getPreview();
        return new TransferProposalCard("TRANSFER_PROPOSAL", proposal.getId(), proposal.statusAt(now),
                preview.amount(), preview.from(), preview.to(), preview.goalDelay(), preview.dailyBudget(),
                preview.fixedImpact(), preview.requiresConfirm(), preview.blocked(), preview.blockedReason(),
                proposal.getExpiresAt());
    }
}
