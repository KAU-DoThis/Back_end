package com.dothis.fintris.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초불이의 방 입장")
public record CoachRoomResponse(
        @Schema(description = "이번 대화 id. 메시지마다 같이 보낸다", example = "6f1c2e0a-3b7d-4c55-9a1e-2d8f0b9c7a41") String sessionId,
        @Schema(description = "첫 인사 (정해 둔 멘트 중 하나)", example = "안녕하세요! 오늘은 뭘 도와드릴까요?") String greeting
) {
}
