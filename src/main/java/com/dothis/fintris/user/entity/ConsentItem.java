package com.dothis.fintris.user.entity;

import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.user.code.ConsentErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Locale;

// 앱 시안 00-2 기준 (API 명세 v4 2장)
// TODO: docUrl은 약관 문서가 올라가면 실제 주소로 바꾼다
@Getter
@AllArgsConstructor
public enum ConsentItem {
    TERMS("서비스 이용약관", null, true, true, "/docs/consents/terms"),
    PRIVACY("개인정보 수집·이용", null, true, true, "/docs/consents/privacy"),
    CODEF("금융거래정보 조회", "CODEF를 통해 연동한 계좌·카드의 잔액과 거래 내역", true, false, "/docs/consents/codef"),
    AGE_14("만 14세 이상이에요", null, true, true, null),
    MARKETING("혜택·소식 알림 받기", null, false, false, "/docs/consents/marketing");

    // 동의 문서는 한 번에 같이 개정한다
    public static final String CURRENT_DOC_VERSION = "2026-10-01";

    private final String title;
    private final String description;
    private final boolean required;
    private final boolean leaveOnWithdraw; // 철회하려면 탈퇴해야 하는 항목
    private final String docUrl;

    // 경로 변수용. 대소문자는 구분하지 않는다
    public static ConsentItem from(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw GeneralException.of(ConsentErrorCode.CONSENT_ITEM_INVALID);
        }
    }
}
