package com.dothis.fintris.coach.service;

import com.dothis.fintris.block.service.BlockService;
import com.dothis.fintris.coach.client.CoachAiClient;
import com.dothis.fintris.coach.client.CoachAiRequest;
import com.dothis.fintris.coach.client.CoachAiResponse;
import com.dothis.fintris.coach.dto.CoachMessageRequest;
import com.dothis.fintris.coach.dto.CoachMessageResponse;
import com.dothis.fintris.coach.dto.CoachRoomResponse;
import com.dothis.fintris.coach.dto.ProposalActionResponse;
import com.dothis.fintris.coach.dto.ProposalCreateRequest;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.waitingfund.service.WaitingFundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// 초불이의 방 입장과 메시지 전달. 대화 기록은 AI 서버가 저장하고, 백엔드는 저장하지 않는다
// AI 호출(최대 15초) 동안 DB 트랜잭션을 잡지 않도록 이 클래스에는 @Transactional을 두지 않는다
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachChatService {

    // TBD(기획): 첫 인사 멘트. 정해지면 바꾼다
    static final List<String> GREETINGS = List.of(
            "안녕하세요! 오늘은 뭘 도와드릴까요?",
            "어서 오세요. 블록 옮기기든 궁금한 거든 말해 주세요.",
            "왔군요! 오늘 돈 관리, 같이 살펴볼까요?");
    static final String AI_UNAVAILABLE_TEXT = "초불이가 잠깐 자리를 비웠어요. 블록 옮기기는 블록 화면에서 할 수 있어요.";
    static final String TRANSFER_RETRY_TEXT = "어느 블록에서 어디로 얼마를 옮길지 다시 말해 주세요.";

    private final CoachAiClient coachAiClient;
    private final CoachProposalService coachProposalService;
    private final BlockService blockService;
    private final WaitingFundService waitingFundService;

    public CoachRoomResponse enter() {
        String greeting = GREETINGS.get(ThreadLocalRandom.current().nextInt(GREETINGS.size()));
        return new CoachRoomResponse(UUID.randomUUID().toString(), greeting);
    }

    public CoachMessageResponse send(Long userId, CoachMessageRequest request) {
        CoachAiRequest aiRequest = new CoachAiRequest(userId, request.sessionId(), request.text(),
                blockService.getBlocks(userId, null), waitingFundService.getWaitingFunds(userId));

        Optional<CoachAiResponse> reply = coachAiClient.chat(aiRequest);
        if (reply.isEmpty()) {
            return new CoachMessageResponse(AI_UNAVAILABLE_TEXT, null, false);
        }
        CoachAiResponse.Transfer transfer = reply.get().transfer();
        if (transfer == null) {
            return new CoachMessageResponse(reply.get().text(), null, true);
        }
        return propose(userId, transfer);
    }

    // 이동 카드 문장은 숫자가 틀리지 않게 서버 문장을 쓴다. AI가 준 블록·금액이 맞지 않으면 다시 묻는다
    private CoachMessageResponse propose(Long userId, CoachAiResponse.Transfer transfer) {
        if (transfer.fromBlockId() == null || transfer.toBlockId() == null
                || transfer.amount() == null || transfer.amount() <= 0) {
            return new CoachMessageResponse(TRANSFER_RETRY_TEXT, null, true);
        }
        try {
            ProposalActionResponse proposal = coachProposalService.propose(userId,
                    new ProposalCreateRequest(transfer.fromBlockId(), transfer.toBlockId(), transfer.amount()));
            return new CoachMessageResponse(proposal.text(), proposal.card(), true);
        } catch (GeneralException e) {
            log.warn("초불이 이동 제안 실패. userId={}, code={}", userId, e.getCode());
            return new CoachMessageResponse(TRANSFER_RETRY_TEXT, null, true);
        }
    }
}
