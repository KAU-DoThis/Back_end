package com.dothis.fintris.notification.entity;

// 알림함 탭 (v4 9장). ALL은 탭 선택용이라 알림 자체의 분류로는 쓰지 않는다
public enum NotificationCategory {
    ALL,
    WARNING,  // 경고
    QUESTION, // 질문
    NEWS      // 소식
}
