package com.dothis.fintris.block.entity;

public enum TransferReason {
    MANUAL,     // 사용자 직접 이동
    SAVING,     // 절약분 나눌 돈에서 배분 (F-05)
    AI,         // AI 제안 수락
    ALLOCATION, // 나눌 돈(정기·추가 입금, 남은 돈)에서 배분
    SUGGESTION, // 제안 카드에서 이동·배분
    UNDO        // 초불이의 방에서 옮긴 것 되돌리기
}
