package com.dothis.fintris.block.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

// blocks.rule JSON 컬럼. 유형별로 쓰는 값만 채우고 나머지는 null
@Schema(description = "블록 규칙. 유형별로 필요한 값만 보낸다")
public record BlockRule(
        @Schema(description = "고정지출형 납부일 1~31", example = "25")
        @JsonProperty("pay_day") Integer payDay,

        @Schema(description = "고정지출형 납부액(원)", example = "600000")
        @JsonProperty("pay_amount") Long payAmount,

        @Schema(description = "목표형 월 확보 금액(원)", example = "150000")
        @JsonProperty("monthly_target") Long monthlyTarget,

        @Schema(description = "고정지출형 거래 매칭 조건(가맹점·수취인)", example = "김OO")
        @JsonProperty("match_condition") String matchCondition
) {
}
