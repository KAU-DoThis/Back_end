package com.dothis.fintris.coach.service;

import com.dothis.fintris.block.code.BlockErrorCode;
import com.dothis.fintris.block.service.BlockService;
import com.dothis.fintris.coach.client.CoachAiClient;
import com.dothis.fintris.coach.client.CoachAiRequest;
import com.dothis.fintris.coach.client.CoachAiResponse;
import com.dothis.fintris.coach.dto.CoachMessageRequest;
import com.dothis.fintris.coach.dto.CoachMessageResponse;
import com.dothis.fintris.coach.dto.CoachRoomResponse;
import com.dothis.fintris.coach.dto.ProposalActionResponse;
import com.dothis.fintris.coach.dto.ProposalCreateRequest;
import com.dothis.fintris.coach.entity.ProposalStatus;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.waitingfund.service.WaitingFundService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachChatServiceTest {

    private static final Long USER_ID = 1L;
    private static final CoachMessageRequest MESSAGE = new CoachMessageRequest("session-1", "여행에서 생활비로 5만원 옮겨줘");

    @Mock CoachAiClient coachAiClient;
    @Mock CoachProposalService coachProposalService;
    @Mock BlockService blockService;
    @Mock WaitingFundService waitingFundService;
    @InjectMocks CoachChatService service;

    @Test
    void 입장하면_대화_id와_정해_둔_인사_중_하나를_준다() {
        CoachRoomResponse first = service.enter();
        CoachRoomResponse second = service.enter();

        assertThat(CoachChatService.GREETINGS).contains(first.greeting(), second.greeting());
        assertThat(first.sessionId()).isNotBlank().isNotEqualTo(second.sessionId());
    }

    @Test
    void 유저_id와_대화_id와_블록_정보를_붙여_AI에_보낸다() {
        when(blockService.getBlocks(USER_ID, null)).thenReturn(List.of());
        when(waitingFundService.getWaitingFunds(USER_ID)).thenReturn(List.of());
        when(coachAiClient.chat(any())).thenReturn(Optional.of(new CoachAiResponse("좋은 하루예요", null)));

        CoachMessageResponse response = service.send(USER_ID, MESSAGE);

        ArgumentCaptor<CoachAiRequest> request = ArgumentCaptor.forClass(CoachAiRequest.class);
        verify(coachAiClient).chat(request.capture());
        assertThat(request.getValue().userId()).isEqualTo(USER_ID);
        assertThat(request.getValue().sessionId()).isEqualTo("session-1");
        assertThat(request.getValue().text()).isEqualTo("여행에서 생활비로 5만원 옮겨줘");
        assertThat(response).isEqualTo(new CoachMessageResponse("좋은 하루예요", null, true));
        verify(coachProposalService, never()).propose(any(), any());
    }

    @Test
    void 이동을_시킨_말이면_제안_카드를_붙인다() {
        when(coachAiClient.chat(any())).thenReturn(Optional.of(
                new CoachAiResponse("옮겨 드릴게요", new CoachAiResponse.Transfer(3L, 1L, 50_000L))));
        Object card = new Object();
        when(coachProposalService.propose(eq(USER_ID), eq(new ProposalCreateRequest(3L, 1L, 50_000L))))
                .thenReturn(new ProposalActionResponse(31L, ProposalStatus.PENDING, "이렇게 옮길까요?", card));

        CoachMessageResponse response = service.send(USER_ID, MESSAGE);

        assertThat(response.text()).isEqualTo("이렇게 옮길까요?");
        assertThat(response.card()).isSameAs(card);
        assertThat(response.aiAvailable()).isTrue();
    }

    @Test
    void AI가_없는_블록을_주면_다시_묻는다() {
        when(coachAiClient.chat(any())).thenReturn(Optional.of(
                new CoachAiResponse("옮겨 드릴게요", new CoachAiResponse.Transfer(99L, 1L, 50_000L))));
        when(coachProposalService.propose(eq(USER_ID), any()))
                .thenThrow(GeneralException.of(BlockErrorCode.BLOCK_NOT_FOUND));

        CoachMessageResponse response = service.send(USER_ID, MESSAGE);

        assertThat(response).isEqualTo(new CoachMessageResponse(CoachChatService.TRANSFER_RETRY_TEXT, null, true));
    }

    @Test
    void 금액이_비었으면_제안하지_않고_다시_묻는다() {
        when(coachAiClient.chat(any())).thenReturn(Optional.of(
                new CoachAiResponse("옮겨 드릴게요", new CoachAiResponse.Transfer(3L, 1L, null))));

        CoachMessageResponse response = service.send(USER_ID, MESSAGE);

        assertThat(response.text()).isEqualTo(CoachChatService.TRANSFER_RETRY_TEXT);
        verify(coachProposalService, never()).propose(any(), any());
    }

    @Test
    void AI_서버가_답하지_못하면_고정_문장으로_답한다() {
        when(coachAiClient.chat(any())).thenReturn(Optional.empty());

        CoachMessageResponse response = service.send(USER_ID, MESSAGE);

        assertThat(response).isEqualTo(new CoachMessageResponse(CoachChatService.AI_UNAVAILABLE_TEXT, null, false));
    }
}
