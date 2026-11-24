package com.dothis.fintris.contextquestion.entity;

public enum QuestionStatus {
    PENDING,  // 발송 대기
    SENT,     // 푸시 발송함
    ANSWERED, // 답함
    EXPIRED   // 7일간 답이 없어 닫음
}
