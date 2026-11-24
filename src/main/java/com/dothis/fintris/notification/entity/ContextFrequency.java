package com.dothis.fintris.notification.entity;

// 맥락 질문 알림 빈도 (v4 9장 알림 설정)
public enum ContextFrequency {
    EACH_SYNC,     // 가져올 때마다
    MAX_3_PER_DAY, // 하루 3번까지
    DAILY_DIGEST   // 하루 1번 저녁 9시에 모아서
}
