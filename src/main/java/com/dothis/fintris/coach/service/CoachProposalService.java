package com.dothis.fintris.coach.service;

import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.block.code.TransferErrorCode;
import com.dothis.fintris.block.dto.TransferRequest;
import com.dothis.fintris.block.dto.TransferResponse;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.entity.TransferReason;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.service.BlockTransferService;
import com.dothis.fintris.block.simulation.BlockSimulationService;
import com.dothis.fintris.block.simulation.ImpactCalculator;
import com.dothis.fintris.block.simulation.SimulateRequest;
import com.dothis.fintris.coach.code.CoachErrorCode;
import com.dothis.fintris.coach.dto.ProposalActionResponse;
import com.dothis.fintris.coach.dto.ProposalCreateRequest;
import com.dothis.fintris.coach.dto.ProposalResponse;
import com.dothis.fintris.coach.dto.TransferProposalCard;
import com.dothis.fintris.coach.dto.TransferResultCard;
import com.dothis.fintris.coach.entity.CoachProposal;
import com.dothis.fintris.coach.entity.ProposalStatus;
import com.dothis.fintris.coach.entity.TransferPreview;
import com.dothis.fintris.coach.entity.TransferResult;
import com.dothis.fintris.coach.repository.CoachProposalRepository;
import com.dothis.fintris.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

// 초불이의 방 블록 이동: 제안 → 수락 → (되돌리기). 계산과 이동은 블록 쪽 서비스를 그대로 쓴다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoachProposalService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // TBD(팀 결정): 제안 만료 시간
    static final long EXPIRE_MINUTES = 10;

    private final BlockSimulationService blockSimulationService;
    private final BlockTransferService blockTransferService;
    private final BlockRepository blockRepository;
    private final CoachProposalRepository coachProposalRepository;

    @Transactional
    public ProposalActionResponse propose(Long userId, ProposalCreateRequest request) {
        if (request.fromBlockId().equals(request.toBlockId())) {
            throw GeneralException.of(TransferErrorCode.TRANSFER_SAME_BLOCK);
        }
        LocalDateTime now = LocalDateTime.now(KST);

        BlockSimulationService.Simulation simulation = blockSimulationService.simulate(userId, List.of(
                new SimulateRequest.Change(request.fromBlockId(), null, request.toBlockId(), request.amount())));
        List<Block> fixedBlocks = blockRepository.findAllByUser_IdAndTypeAndDeletedAtIsNullOrderByDisplayOrderAsc(
                userId, BlockType.FIXED);
        TransferPreview preview = TransferPreviewBuilder.build(simulation, request.fromBlockId(),
                request.toBlockId(), request.amount(), fixedBlocks, now.toLocalDate());

        Block from = simulation.blocks().get(request.fromBlockId());
        CoachProposal proposal = coachProposalRepository.save(CoachProposal.builder()
                .user(from.getUser())
                .fromBlock(from)
                .toBlock(simulation.blocks().get(request.toBlockId()))
                .amount(request.amount())
                .preview(preview)
                .expiresAt(now.plusMinutes(EXPIRE_MINUTES))
                .build());

        // 수락할 수 없는 제안도 카드는 보여 주고 이유를 말한다
        String text = preview.blocked() ? preview.blockedReason() : "이렇게 옮길까요?";
        return new ProposalActionResponse(proposal.getId(), proposal.getStatus(), text,
                TransferProposalCard.of(proposal, now));
    }

    public ProposalResponse getProposal(Long userId, Long proposalId) {
        return ProposalResponse.of(findOwned(userId, proposalId), LocalDateTime.now(KST));
    }

    @Transactional
    public ProposalActionResponse accept(Long userId, Long proposalId, Boolean confirmed) {
        CoachProposal proposal = findOwned(userId, proposalId);
        checkPending(proposal, LocalDateTime.now(KST));

        // 제안 뒤에 블록 잔액이 바뀌었으면 카드에 보여 준 숫자와 달라지므로 옮기지 않는다
        TransferPreview preview = proposal.getPreview();
        checkUnchanged(preview.from());
        checkUnchanged(preview.to());

        TransferResponse transfer = blockTransferService.transfer(userId, new TransferRequest(
                preview.from().blockId(), preview.to().blockId(), proposal.getAmount(), TransferReason.AI,
                preview.from().version(), preview.to().version(), confirmed));
        proposal.accept(transfer.transferId(),
                new TransferResult(transfer.fromBlock().balance(), transfer.toBlock().balance()));

        return new ProposalActionResponse(proposal.getId(), proposal.getStatus(),
                ImpactCalculator.won(proposal.getAmount()) + "원 옮겼어요",
                TransferResultCard.transferred(proposal));
    }

    @Transactional
    public ProposalActionResponse reject(Long userId, Long proposalId) {
        CoachProposal proposal = findOwned(userId, proposalId);
        checkPending(proposal, LocalDateTime.now(KST));
        proposal.reject();
        return new ProposalActionResponse(proposal.getId(), proposal.getStatus(), "알겠어요, 그대로 둘게요", null);
    }

    // 받은 블록 → 보낸 블록으로 같은 금액을 한 번 더 옮긴다. 한 번만, 전액만
    @Transactional
    public ProposalActionResponse undo(Long userId, Long proposalId) {
        CoachProposal proposal = findOwned(userId, proposalId);
        if (proposal.getStatus() != ProposalStatus.ACCEPTED) {
            throw GeneralException.of(CoachErrorCode.COACH_PROPOSAL_NOT_UNDOABLE);
        }
        TransferPreview preview = proposal.getPreview();
        Block received = blockRepository.findByIdAndDeletedAtIsNull(preview.to().blockId())
                .orElseThrow(() -> GeneralException.of(CoachErrorCode.COACH_PROPOSAL_NOT_UNDOABLE));
        blockRepository.findByIdAndDeletedAtIsNull(preview.from().blockId())
                .orElseThrow(() -> GeneralException.of(CoachErrorCode.COACH_PROPOSAL_NOT_UNDOABLE));
        if (received.getBalance() < proposal.getAmount()) {
            throw GeneralException.of(CoachErrorCode.COACH_UNDO_INSUFFICIENT_BALANCE);
        }

        // 원래 상태로 돌려놓는 이동이라 규칙 확인(requiresConfirm)은 다시 묻지 않는다
        TransferResponse transfer = blockTransferService.transfer(userId, new TransferRequest(
                preview.to().blockId(), preview.from().blockId(), proposal.getAmount(), TransferReason.UNDO,
                null, null, true));
        proposal.undo(transfer.transferId(),
                new TransferResult(transfer.fromBlock().balance(), transfer.toBlock().balance()));

        return new ProposalActionResponse(proposal.getId(), proposal.getStatus(), "되돌렸어요",
                TransferResultCard.undone(proposal));
    }

    private void checkPending(CoachProposal proposal, LocalDateTime now) {
        ProposalStatus status = proposal.statusAt(now);
        if (status == ProposalStatus.EXPIRED) {
            throw GeneralException.of(CoachErrorCode.COACH_PROPOSAL_EXPIRED);
        }
        if (status != ProposalStatus.PENDING) {
            throw GeneralException.of(CoachErrorCode.COACH_PROPOSAL_ALREADY_HANDLED);
        }
    }

    // 블록이 지워졌거나 version이 올라갔으면 STALE
    private void checkUnchanged(TransferPreview.Side side) {
        Block block = blockRepository.findByIdAndDeletedAtIsNull(side.blockId())
                .orElseThrow(() -> GeneralException.of(CoachErrorCode.COACH_PROPOSAL_STALE));
        if (!Objects.equals(block.getVersion(), side.version())) {
            throw GeneralException.of(CoachErrorCode.COACH_PROPOSAL_STALE);
        }
    }

    private CoachProposal findOwned(Long userId, Long proposalId) {
        CoachProposal proposal = coachProposalRepository.findById(proposalId)
                .orElseThrow(() -> GeneralException.of(CoachErrorCode.COACH_PROPOSAL_NOT_FOUND));
        if (!proposal.isOwnedBy(userId)) {
            throw GeneralException.of(CoachErrorCode.COACH_PROPOSAL_FORBIDDEN);
        }
        return proposal;
    }
}
