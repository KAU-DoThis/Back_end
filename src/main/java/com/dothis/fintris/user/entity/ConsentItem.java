package com.dothis.fintris.user.entity;

import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.user.code.UserErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Locale;

// TODO: docUrl은 약관 문서가 올라가면 실제 주소로 바꾼다
@Getter
@AllArgsConstructor
public enum ConsentItem {
    TERMS("서비스 이용약관", "FINTRIS 서비스 이용 조건을 정합니다.",
            ConsentRequirement.ALWAYS, "/docs/consents/terms"),
    PRIVACY("개인정보 수집·이용", "가입과 서비스 제공에 필요한 개인정보를 수집·이용합니다.",
            ConsentRequirement.ALWAYS, "/docs/consents/privacy"),
    CODEF("금융 거래정보 조회 (CODEF)", "연동한 계좌·카드의 잔액과 거래내역을 조회합니다.",
            ConsentRequirement.ON_LINK, "/docs/consents/codef"),
    AI_PROCESSING("AI 분석을 위한 데이터 처리", "거래 분류와 절약 제안을 위해 금액·카테고리를 분석합니다.",
            ConsentRequirement.ON_LINK, "/docs/consents/ai-processing"),
    NOTIFICATION_READ("결제 알림 읽기", "결제 알림을 읽어 블록에 바로 반영합니다.",
            ConsentRequirement.OPTIONAL, "/docs/consents/notification-read"),
    PUSH("푸시 알림 수신", "오늘 쓸 수 있는 돈과 블록 상태 변화를 알려 드립니다.",
            ConsentRequirement.OPTIONAL, "/docs/consents/push"),
    PRODUCT_INFO("금융상품 비교 정보 수신", "목표에 맞는 금융상품 비교 정보를 보여 드립니다.",
            ConsentRequirement.OPTIONAL, "/docs/consents/product-info");

    // 동의 문서는 한 번에 같이 개정한다
    public static final String CURRENT_DOC_VERSION = "2026-10-01";

    private final String title;
    private final String purpose;
    private final ConsentRequirement requirement;
    private final String docUrl;

    public boolean isRequired() {
        return requirement != ConsentRequirement.OPTIONAL;
    }

    // 경로 변수용. 대소문자는 구분하지 않는다
    public static ConsentItem from(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw GeneralException.of(UserErrorCode.USER_CONSENT_ITEM_INVALID);
        }
    }
}
