package com.dothis.fintris.waitingfund.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 나눌 돈이 어디서 왔는지 (API 명세 v4 6-1장)
@Getter
@AllArgsConstructor
public enum WaitingFundSource {
    SAVING("절약분"),                // 절약 목표 확정 시 생활비에서 꺼낸 돈
    REGULAR_INCOME("정기 입금"),     // 월급·용돈
    EXTRA_INCOME("추가 입금"),       // 정기가 아닌 입금
    OTHER("남은 돈");                // 초기 구성 후 남은 돈, 블록 삭제 반환, 불변식 보정

    private final String defaultLabel;
}
