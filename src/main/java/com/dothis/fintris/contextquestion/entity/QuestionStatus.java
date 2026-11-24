package com.dothis.fintris.contextquestion.entity;

import java.util.List;

public enum QuestionStatus {
    PENDING,  // 발송 대기
    SENT,     // 푸시 발송함
    ANSWERED, // 답함
    EXPIRED;  // 7일간 답이 없어 닫음

    // 아직 답하지 않은 질문. 푸시를 보낸 것도 포함한다
    public static final List<QuestionStatus> OPEN = List.of(PENDING, SENT);
}
