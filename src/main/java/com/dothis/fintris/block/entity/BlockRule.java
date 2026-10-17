package com.dothis.fintris.block.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

// blocks.rule JSON 컬럼. 유형별로 쓰는 값만 채우고 나머지는 null
public record BlockRule(
        @JsonProperty("pay_day") Integer payDay,               // 고정지출형 납부일 1~31
        @JsonProperty("pay_amount") Long payAmount,            // 고정지출형 납부액
        @JsonProperty("monthly_target") Long monthlyTarget,    // 목표형 월 확보 금액
        @JsonProperty("match_condition") String matchCondition // 고정지출형 거래 매칭 조건(가맹점·수취인)
) {
}
