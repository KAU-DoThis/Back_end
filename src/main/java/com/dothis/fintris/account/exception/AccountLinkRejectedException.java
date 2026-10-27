package com.dothis.fintris.account.exception;

import com.dothis.fintris.account.code.AccountErrorCode;
import com.dothis.fintris.global.exception.GeneralException;
import lombok.Getter;

// 금융기관이 인증을 거절한 경우. 기관이 알려준 사유를 응답에 함께 전달한다
@Getter
public class AccountLinkRejectedException extends GeneralException {

    private final String institutionMessage;

    public AccountLinkRejectedException(String institutionMessage) {
        super(AccountErrorCode.ACCOUNT_LINK_REJECTED);
        this.institutionMessage = institutionMessage;
    }
}
