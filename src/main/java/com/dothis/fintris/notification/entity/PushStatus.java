package com.dothis.fintris.notification.entity;

// 알림 한 건의 푸시 발송 상태 (F-22). 알림함에는 상태와 관계없이 남는다
public enum PushStatus {
    HELD,    // 받지 않는 시간·모아 보내기라 미룸
    SENT,    // 기기 하나 이상에 보냄
    SKIPPED, // 푸시를 껐거나 하루 건수를 넘었거나 등록된 기기가 없음
    FAILED   // 모든 기기에서 실패
}
