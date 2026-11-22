package com.dothis.fintris.goal.dto;

// 목표 달성(17)·기한 경과(17-1) 화면에서 고르는 처리 (API 명세 v4 7장)
public enum GoalAction {
    KEEP,              // 달성: 그대로 지키기, 매달 넣는 건 멈춤
    RAISE_TARGET,      // 달성: 목표 올리기
    MOVE,              // 달성: 다른 블록으로 옮기기
    EXTEND,            // 기한 지남: 기한 늘리기
    FINISH_AT_CURRENT, // 기한 지남: 모은 만큼으로 끝내기
    CLEANUP,           // 기한 지남: 블록 정리
    REMIND_LATER       // 둘 다: 지금 정하지 않음
}
