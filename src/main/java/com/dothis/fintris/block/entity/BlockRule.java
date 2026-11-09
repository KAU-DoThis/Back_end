package com.dothis.fintris.block.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

// API 명세 v4 블록 객체의 rule. 유형별로 필요한 값만 쓴다
// 예전 snake_case 키(pay_day 등)로 저장된 행도 읽을 수 있게 별칭을 둔다. 값이 없는 키는 내보내지 않는다
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "블록 규칙. 유형별로 필요한 값만 보낸다")
public record BlockRule(
        @Schema(description = "사용형 월 배분액(원)", example = "1500000")
        Long monthlyAmount,

        @Schema(description = "사용형 기간 시작일(매월 n일)", example = "1")
        Integer startDay,

        @Schema(description = "목표형 월 확보 금액(원). 비우면 권장값으로 채운다", example = "200000")
        @JsonAlias("monthly_target") Long monthlyTarget,

        @Schema(description = "고정지출형 납부일 1~31 (그 달에 없으면 말일)", example = "25")
        @JsonAlias("pay_day") Integer payDay,

        @Schema(description = "고정지출형 납부액(원)", example = "600000")
        @JsonAlias("pay_amount") Long payAmount,

        @Schema(description = "고정지출형 납부 확인 조건")
        @JsonAlias("match_condition") MatchCondition matchCondition,

        @Schema(description = "고정지출형 알림 설정")
        Alerts alerts
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "납부 확인 조건. 이 조건에 맞는 출금이 나가면 납부 완료로 본다")
    public record MatchCondition(
            @Schema(description = "받는 분(마스킹)", example = "김*수") String payee,
            @Schema(description = "EXACT: 그대로, RANGE: 범위", example = "EXACT") AmountMode amountMode,
            @Schema(example = "600000") Long minAmount,
            @Schema(example = "600000") Long maxAmount,
            @Schema(description = "납부일 앞뒤 허용 일수", example = "3") Integer dayWindow,
            @Schema(description = "나가는 계좌", example = "3") Long linkedAccountId
    ) {
        // 객체로 오면 각 키를 그대로 쓴다
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public MatchCondition(@JsonProperty("payee") String payee,
                              @JsonProperty("amountMode") AmountMode amountMode,
                              @JsonProperty("minAmount") Long minAmount,
                              @JsonProperty("maxAmount") Long maxAmount,
                              @JsonProperty("dayWindow") Integer dayWindow,
                              @JsonProperty("linkedAccountId") Long linkedAccountId) {
            this.payee = payee;
            this.amountMode = amountMode;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.dayWindow = dayWindow;
            this.linkedAccountId = linkedAccountId;
        }

        // 예전 규칙은 match_condition이 "김OO" 같은 받는 분 문자열이었다. 버리지 않고 payee로 옮긴다
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static MatchCondition fromLegacy(String payee) {
            return new MatchCondition(payee, null, null, null, null, null);
        }
    }

    public enum AmountMode {
        EXACT, RANGE
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "고정지출형 알림")
    public record Alerts(
            @Schema(description = "납부 3일 전까지 모자라면 알림", example = "true") Boolean shortageBefore3Days,
            @Schema(description = "다른 블록에서 채울지 같이 묻기", example = "true") Boolean askFillFromOther,
            @Schema(description = "납부 확인되면 알림", example = "false") Boolean paidConfirmed
    ) {
        Alerts mergedWith(Alerts patch) {
            if (patch == null) return this;
            return new Alerts(
                    patch.shortageBefore3Days != null ? patch.shortageBefore3Days : shortageBefore3Days,
                    patch.askFillFromOther != null ? patch.askFillFromOther : askFillFromOther,
                    patch.paidConfirmed != null ? patch.paidConfirmed : paidConfirmed
            );
        }
    }

    // PATCH: 보낸 키만 바꾼다. matchCondition은 통째로, alerts는 키별로 바꾼다
    public BlockRule mergedWith(BlockRule patch) {
        if (patch == null) return this;
        return new BlockRule(
                patch.monthlyAmount != null ? patch.monthlyAmount : monthlyAmount,
                patch.startDay != null ? patch.startDay : startDay,
                patch.monthlyTarget != null ? patch.monthlyTarget : monthlyTarget,
                patch.payDay != null ? patch.payDay : payDay,
                patch.payAmount != null ? patch.payAmount : payAmount,
                patch.matchCondition != null ? patch.matchCondition : matchCondition,
                alerts == null ? patch.alerts : alerts.mergedWith(patch.alerts)
        );
    }

    public BlockRule withMonthlyTarget(Long monthlyTarget) {
        return new BlockRule(monthlyAmount, startDay, monthlyTarget, payDay, payAmount, matchCondition, alerts);
    }

    public static BlockRule empty() {
        return new BlockRule(null, null, null, null, null, null, null);
    }
}
