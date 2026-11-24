package com.dothis.fintris.contextquestion.entity;

// AI 분류 응답의 questionType (BE↔AI 연동 명세 5절)
public enum QuestionType {
    UNKNOWN_ITEM,      // 간편결제·오픈마켓, 무엇을 샀는지 모름
    PERSONAL_TRANSFER, // 1만 원 이상 개인 송금
    LOW_CONFIDENCE,    // 분류 신뢰도 0.7 미만
    LATE_NIGHT_FOOD    // 22시 이후 배달·외식. 카테고리는 정해졌고 맥락만 묻는다
}
