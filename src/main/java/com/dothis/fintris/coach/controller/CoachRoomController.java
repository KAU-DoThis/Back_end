package com.dothis.fintris.coach.controller;

import com.dothis.fintris.coach.dto.CoachMessageRequest;
import com.dothis.fintris.coach.dto.CoachMessageResponse;
import com.dothis.fintris.coach.dto.CoachRoomResponse;
import com.dothis.fintris.coach.service.CoachChatService;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CoachRoom", description = "초불이의 방 (F-16)")
@RestController
@RequestMapping("/coach/room")
@RequiredArgsConstructor
public class CoachRoomController {

    private final CoachChatService coachChatService;

    @Operation(summary = "방 입장",
            description = "이번 대화 id와 첫 인사(정해 둔 멘트 중 랜덤)를 준다. 방에 다시 들어오면 새 대화로 시작한다")
    @GetMapping
    public ApiResponse<CoachRoomResponse> enter(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, coachChatService.enter());
    }

    @Operation(summary = "초불이에게 말 걸기",
            description = "블록·나눌 돈 정보를 붙여 AI 서버에 보낸다. 블록 이동을 시킨 말이면 이동 제안 카드(아직 옮기지 않음)를 같이 준다. "
                    + "AI 서버가 답하지 못하면 aiAvailable=false와 고정 문장")
    @PostMapping("/messages")
    public ApiResponse<CoachMessageResponse> send(@AuthenticationPrincipal Long userId,
                                                  @RequestBody @Valid CoachMessageRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, coachChatService.send(userId, request));
    }
}
