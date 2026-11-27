package com.dothis.fintris.saving.entity;

// 절약 목표 상태 (API 명세 v4 8장). ALLOCATED는 저장하지 않고, 확정으로 만든 나눌 돈을 다 나눴는지로 응답 때 정한다
public enum SavingGoalStatus {
    ACTIVE,    // 기간 중
    CONFIRMED, // 기간이 끝나 절약액을 확정하고 나눌 돈(절약분)으로 옮김
    ALLOCATED, // 그 나눌 돈을 모두 블록에 나눔
    CANCELED   // 사용자가 취소
}
