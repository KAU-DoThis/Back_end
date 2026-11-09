package com.dothis.fintris.transaction.dto;

public enum SyncStatus {
    IDLE,        // 진행 중이 아님 (서버를 다시 켠 뒤에도 이 값)
    IN_PROGRESS, // 가져오는 중
    DONE,        // 마지막 동기화가 끝남
    FAILED,      // 마지막 동기화에서 모든 계좌가 실패함
    SKIPPED      // 시작 요청 응답에만 쓴다. 마지막 동기화가 1시간 이내라 건너뜀
}
