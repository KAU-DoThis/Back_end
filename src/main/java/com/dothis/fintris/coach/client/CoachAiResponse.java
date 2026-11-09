package com.dothis.fintris.coach.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// AI 서버 → Spring. 블록 이동을 시킨 말이면 transfer에 값을 담는다. 숫자 계산은 Spring이 한다
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoachAiResponse(String text, Transfer transfer) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transfer(Long fromBlockId, Long toBlockId, Long amount) {
    }
}
