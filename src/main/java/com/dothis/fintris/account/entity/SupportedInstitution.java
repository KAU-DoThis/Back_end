package com.dothis.fintris.account.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

// 아이디·비밀번호 로그인으로 연동할 수 있는 금융기관 (CODEF 기관코드)
@Getter
@RequiredArgsConstructor
public enum SupportedInstitution {

    IBK("0003", "기업은행", LinkedAccountType.BANK),
    KOOKMIN("0004", "국민은행", LinkedAccountType.BANK),
    NONGHYUP("0011", "농협은행", LinkedAccountType.BANK),
    WOORI("0020", "우리은행", LinkedAccountType.BANK),
    SC("0023", "SC제일은행", LinkedAccountType.BANK),
    CITI("0027", "씨티은행", LinkedAccountType.BANK),
    DAEGU("0031", "대구은행", LinkedAccountType.BANK),
    JEONBUK("0037", "전북은행", LinkedAccountType.BANK),
    KYONGNAM("0039", "경남은행", LinkedAccountType.BANK),
    SAEMAEUL("0045", "새마을금고", LinkedAccountType.BANK),
    POST("0071", "우체국", LinkedAccountType.BANK),
    HANA("0081", "KEB하나은행", LinkedAccountType.BANK),
    SHINHAN("0088", "신한은행", LinkedAccountType.BANK);

    private final String code;
    private final String displayName;
    private final LinkedAccountType type;

    public static Optional<SupportedInstitution> findByCode(String code) {
        return Arrays.stream(values())
                .filter(institution -> institution.code.equals(code))
                .findFirst();
    }
}
