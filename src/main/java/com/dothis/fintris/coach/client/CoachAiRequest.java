package com.dothis.fintris.coach.client;

import com.dothis.fintris.block.dto.BlockResponse;
import com.dothis.fintris.waitingfund.dto.WaitingFundResponse;

import java.util.List;

// Spring → AI 서버. AI가 백엔드에 블록을 직접 조회하기 전까지는 블록·나눌 돈 정보를 매번 같이 보낸다
// sessionId: 방에 들어올 때 발급. AI 서버가 이 값으로 이번 대화를 묶어 기록한다
public record CoachAiRequest(
        Long userId,
        String sessionId,
        String text,
        List<BlockResponse> blocks,
        List<WaitingFundResponse> waitingFunds
) {
}
