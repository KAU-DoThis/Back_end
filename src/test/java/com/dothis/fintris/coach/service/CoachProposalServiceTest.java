package com.dothis.fintris.coach.service;

import com.dothis.fintris.block.code.TransferErrorCode;
import com.dothis.fintris.block.dto.TransferRequest;
import com.dothis.fintris.block.dto.TransferResponse;
import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.block.entity.BlockStatus;
import com.dothis.fintris.block.entity.BlockType;
import com.dothis.fintris.block.entity.TransferReason;
import com.dothis.fintris.block.repository.BlockRepository;
import com.dothis.fintris.block.service.BlockTransferService;
import com.dothis.fintris.block.simulation.BlockSimulationService;
import com.dothis.fintris.coach.code.CoachErrorCode;
import com.dothis.fintris.coach.dto.ProposalActionResponse;
import com.dothis.fintris.coach.dto.ProposalCreateRequest;
import com.dothis.fintris.coach.dto.TransferResultCard;
import com.dothis.fintris.coach.entity.CoachProposal;
import com.dothis.fintris.coach.entity.ProposalStatus;
import com.dothis.fintris.coach.entity.TransferPreview;
import com.dothis.fintris.coach.entity.TransferResult;
import com.dothis.fintris.coach.repository.CoachProposalRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachProposalServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 1L;
    private static final Long PROPOSAL_ID = 31L;

    @Mock BlockSimulationService blockSimulationService;
    @Mock BlockTransferService blockTransferService;
    @Mock BlockRepository blockRepository;
    @Mock CoachProposalRepository coachProposalRepository;
    @InjectMocks CoachProposalService service;

    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
    private Block travel;
    private Block living;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        travel = block(3L, "여행", 320_000);
        living = block(1L, "생활비", 950_000);
    }

    @Test
    void 수락하면_AI_이동으로_옮기고_결과_카드를_준다() {
        CoachProposal proposal = proposal(LocalDateTime.now(KST).plusMinutes(5));
        when(coachProposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(blockRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(travel));
        when(blockRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(living));
        when(blockTransferService.transfer(eq(USER_ID), any())).thenReturn(transferResponse(34L, 270_000, 1_000_000));

        ProposalActionResponse response = service.accept(USER_ID, PROPOSAL_ID, null);

        ArgumentCaptor<TransferRequest> request = ArgumentCaptor.forClass(TransferRequest.class);
        verify(blockTransferService).transfer(eq(USER_ID), request.capture());
        assertThat(request.getValue()).isEqualTo(
                new TransferRequest(3L, 1L, 50_000L, TransferReason.AI, 4L, 4L, null));

        assertThat(response.status()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(response.text()).isEqualTo("50,000원 옮겼어요");
        TransferResultCard card = (TransferResultCard) response.card();
        assertThat(card.from()).isEqualTo(new TransferResultCard.Side(3L, "여행", 270_000, -50_000));
        assertThat(card.to()).isEqualTo(new TransferResultCard.Side(1L, "생활비", 1_000_000, 50_000));
        assertThat(card.undoable()).isTrue();
        assertThat(card.actions()).containsExactly("UNDO", "OPEN_HOME");
        assertThat(proposal.getTransferId()).isEqualTo(34L);
    }

    @Test
    void 제안_뒤에_블록이_바뀌었으면_옮기지_않는다() {
        when(coachProposalRepository.findById(PROPOSAL_ID))
                .thenReturn(Optional.of(proposal(LocalDateTime.now(KST).plusMinutes(5))));
        ReflectionTestUtils.setField(travel, "version", 5L);
        when(blockRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(travel));

        assertThatThrownBy(() -> service.accept(USER_ID, PROPOSAL_ID, null))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(CoachErrorCode.COACH_PROPOSAL_STALE);
        verify(blockTransferService, never()).transfer(any(), any());
    }

    @Test
    void 만료된_제안은_수락할_수_없다() {
        when(coachProposalRepository.findById(PROPOSAL_ID))
                .thenReturn(Optional.of(proposal(LocalDateTime.now(KST).minusSeconds(1))));

        assertThatThrownBy(() -> service.accept(USER_ID, PROPOSAL_ID, null))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(CoachErrorCode.COACH_PROPOSAL_EXPIRED);
    }

    @Test
    void 이미_수락한_제안은_다시_수락할_수_없다() {
        CoachProposal proposal = proposal(LocalDateTime.now(KST).plusMinutes(5));
        proposal.accept(34L, new TransferResult(270_000, 1_000_000));
        when(coachProposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> service.accept(USER_ID, PROPOSAL_ID, null))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(CoachErrorCode.COACH_PROPOSAL_ALREADY_HANDLED);
    }

    @Test
    void 다른_사람의_제안은_볼_수_없다() {
        User other = User.builder().email("other@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(other, "id", 2L);
        CoachProposal proposal = proposal(LocalDateTime.now(KST).plusMinutes(5));
        ReflectionTestUtils.setField(proposal, "user", other);
        when(coachProposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> service.getProposal(USER_ID, PROPOSAL_ID))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(CoachErrorCode.COACH_PROPOSAL_FORBIDDEN);
    }

    @Test
    void 되돌리면_받은_블록에서_보낸_블록으로_옮긴다() {
        CoachProposal proposal = proposal(LocalDateTime.now(KST).plusMinutes(5));
        proposal.accept(34L, new TransferResult(270_000, 1_000_000));
        ReflectionTestUtils.setField(living, "balance", 1_000_000L);
        when(coachProposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(blockRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(living));
        when(blockRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(travel));
        when(blockTransferService.transfer(eq(USER_ID), any())).thenReturn(transferResponse(35L, 950_000, 320_000));

        ProposalActionResponse response = service.undo(USER_ID, PROPOSAL_ID);

        ArgumentCaptor<TransferRequest> request = ArgumentCaptor.forClass(TransferRequest.class);
        verify(blockTransferService).transfer(eq(USER_ID), request.capture());
        assertThat(request.getValue()).isEqualTo(
                new TransferRequest(1L, 3L, 50_000L, TransferReason.UNDO, null, null, true));

        assertThat(response.status()).isEqualTo(ProposalStatus.UNDONE);
        assertThat(response.text()).isEqualTo("되돌렸어요");
        TransferResultCard card = (TransferResultCard) response.card();
        assertThat(card.from()).isEqualTo(new TransferResultCard.Side(1L, "생활비", 950_000, -50_000));
        assertThat(card.to()).isEqualTo(new TransferResultCard.Side(3L, "여행", 320_000, 50_000));
        assertThat(card.undo()).isTrue();
        assertThat(card.undoable()).isFalse();
    }

    @Test
    void 받은_블록_잔액이_모자라면_되돌리지_않는다() {
        CoachProposal proposal = proposal(LocalDateTime.now(KST).plusMinutes(5));
        proposal.accept(34L, new TransferResult(270_000, 1_000_000));
        ReflectionTestUtils.setField(living, "balance", 30_000L);
        when(coachProposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(blockRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(living));
        when(blockRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(travel));

        assertThatThrownBy(() -> service.undo(USER_ID, PROPOSAL_ID))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(CoachErrorCode.COACH_UNDO_INSUFFICIENT_BALANCE);
        verify(blockTransferService, never()).transfer(any(), any());
    }

    @Test
    void 수락하지_않은_제안은_되돌릴_수_없다() {
        when(coachProposalRepository.findById(PROPOSAL_ID))
                .thenReturn(Optional.of(proposal(LocalDateTime.now(KST).plusMinutes(5))));

        assertThatThrownBy(() -> service.undo(USER_ID, PROPOSAL_ID))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(CoachErrorCode.COACH_PROPOSAL_NOT_UNDOABLE);
    }

    @Test
    void 같은_블록끼리는_제안하지_않는다() {
        assertThatThrownBy(() -> service.propose(USER_ID, new ProposalCreateRequest(3L, 3L, 50_000L)))
                .isInstanceOf(GeneralException.class)
                .extracting("code").isEqualTo(TransferErrorCode.TRANSFER_SAME_BLOCK);
        verify(blockSimulationService, never()).simulate(any(), any());
    }

    private CoachProposal proposal(LocalDateTime expiresAt) {
        TransferPreview preview = new TransferPreview(50_000,
                new TransferPreview.Side(3L, "여행", BlockType.GOAL, 320_000, 270_000, 4L),
                new TransferPreview.Side(1L, "생활비", BlockType.SPENDING, 950_000, 1_000_000, 4L),
                null, null, new TransferPreview.FixedImpact(false, List.of()), false, false, null);
        CoachProposal proposal = CoachProposal.builder()
                .user(user)
                .fromBlock(travel)
                .toBlock(living)
                .amount(50_000)
                .preview(preview)
                .expiresAt(expiresAt)
                .build();
        ReflectionTestUtils.setField(proposal, "id", PROPOSAL_ID);
        return proposal;
    }

    private TransferResponse transferResponse(Long transferId, long fromBalance, long toBalance) {
        return new TransferResponse(transferId,
                new TransferResponse.BlockBalance(0L, fromBalance, BlockStatus.SAFE, 5L),
                new TransferResponse.BlockBalance(0L, toBalance, BlockStatus.SAFE, 5L),
                null, null);
    }

    private Block block(Long id, String name, long balance) {
        Block block = Block.builder()
                .user(user)
                .type(BlockType.GOAL)
                .name(name)
                .balance(balance)
                .startDate(LocalDate.of(2026, 11, 1))
                .build();
        ReflectionTestUtils.setField(block, "id", id);
        ReflectionTestUtils.setField(block, "version", 4L);
        return block;
    }
}
