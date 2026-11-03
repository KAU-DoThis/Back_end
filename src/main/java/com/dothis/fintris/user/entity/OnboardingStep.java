package com.dothis.fintris.user.entity;

// 앱 시작 시 FE가 이 단계 화면으로 보낸다. 저장하지 않고 동의·연동·설정 상태로 계산한다
public enum OnboardingStep {
    CONSENT,   // 필수 동의 없음
    LINK,      // 연동 계좌 없음
    TUTORIAL,  // 튜토리얼 안 봄
    BLOCKS,    // 초기 블록 확정 안 함
    DONE
}
