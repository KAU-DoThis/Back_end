package com.dothis.fintris.coach.controller;

import com.dothis.fintris.coach.dto.ProposalAcceptRequest;
import com.dothis.fintris.coach.dto.ProposalActionResponse;
import com.dothis.fintris.coach.dto.ProposalCreateRequest;
import com.dothis.fintris.coach.dto.ProposalResponse;
import com.dothis.fintris.coach.service.CoachProposalService;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CoachProposal", description = "초불이의 방 블록 이동 제안 (F-16)")
@RestController
@RequestMapping("/coach/proposals")
@RequiredArgsConstructor
public class CoachProposalController {

    private final CoachProposalService coachProposalService;

    @Operation(summary = "이동 제안 만들기",
            description = "아직 옮기지 않는다. /blocks/simulate와 같은 계산으로 보내는·받는 블록 전후 금액, 목표 지연, "
                    + "하루 쓸 돈, 고정지출 영향을 담은 카드를 돌려준다. 10분 뒤 만료")
    @PostMapping
    public ApiResponse<ProposalActionResponse> propose(@AuthenticationPrincipal Long userId,
                                                       @RequestBody @Valid ProposalCreateRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, coachProposalService.propose(userId, request));
    }

    @Operation(summary = "제안 상태와 카드", description = "대화 기록에 남은 proposalId로 카드를 다시 그릴 때 쓴다")
    @GetMapping("/{proposalId}")
    public ApiResponse<ProposalResponse> getProposal(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long proposalId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, coachProposalService.getProposal(userId, proposalId));
    }

    @Operation(summary = "제안 수락 (옮기기)",
            description = "제안 뒤에 블록이 바뀌었으면 COACH_PROPOSAL_STALE, 만료면 COACH_PROPOSAL_EXPIRED, "
                    + "requiresConfirm인데 confirmed가 없으면 TRANSFER_CONFIRM_REQUIRED")
    @PostMapping("/{proposalId}/accept")
    public ApiResponse<ProposalActionResponse> accept(@AuthenticationPrincipal Long userId,
                                                      @PathVariable Long proposalId,
                                                      @RequestBody(required = false) ProposalAcceptRequest request) {
        Boolean confirmed = (request != null) ? request.confirmed() : null;
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED,
                coachProposalService.accept(userId, proposalId, confirmed));
    }

    @Operation(summary = "제안 거절")
    @PostMapping("/{proposalId}/reject")
    public ApiResponse<ProposalActionResponse> reject(@AuthenticationPrincipal Long userId,
                                                      @PathVariable Long proposalId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, coachProposalService.reject(userId, proposalId));
    }

    @Operation(summary = "옮긴 것 되돌리기",
            description = "받은 블록에서 보낸 블록으로 같은 금액을 옮긴다. 한 번만, 전액만. "
                    + "받은 블록 잔액이 모자라면 COACH_UNDO_INSUFFICIENT_BALANCE")
    @PostMapping("/{proposalId}/undo")
    public ApiResponse<ProposalActionResponse> undo(@AuthenticationPrincipal Long userId,
                                                    @PathVariable Long proposalId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, coachProposalService.undo(userId, proposalId));
    }
}
