package com.dothis.fintris.user.entity;

public enum ConsentItem {
    TERMS,              // 서비스 이용약관 (필수)
    PRIVACY,            // 개인정보 수집·이용 (필수)
    CODEF,              // 금융 거래정보 조회 (연동 시 필수)
    AI_PROCESSING,      // AI 분석을 위한 데이터 처리 (연동 시 필수)
    NOTIFICATION_READ,  // 결제 알림 읽기 (선택)
    PUSH,               // 푸시 알림 수신 (선택)
    PRODUCT_INFO        // 금융상품 비교 정보 수신 (선택)
}
