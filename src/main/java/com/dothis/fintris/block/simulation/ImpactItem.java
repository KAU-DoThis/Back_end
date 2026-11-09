package com.dothis.fintris.block.simulation;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

// 이동·배분 미리보기 결과 한 줄 (API 명세 v4 0장 ImpactItem). 문장은 서버가 틀에 숫자를 채워 만든다
@Schema(description = "미리보기 결과 항목")
public record ImpactItem(
        @Schema(example = "goal_date") Type type,
        @Schema(description = "danger(규칙 깨짐) · warn(나빠짐) · good(좋아지거나 지켜짐) · info(숫자만 바뀜)", example = "warn")
        Severity severity,
        @Schema(example = "3") Long blockId,
        @Schema(example = "여행 목표가 약 2주 늦어져요") String title,
        @Schema(example = "이번 달 확보액 120,000 → 70,000 (월 목표 200,000)") String detail,
        @Schema(example = "2026-12-31") String before,
        @Schema(example = "2027-01-14") String after
) {

    public enum Type {
        STATUS_CHANGE("status_change"),
        GOAL_DATE("goal_date"),
        MONTHLY_SECURED("monthly_secured"),
        PAYMENT("payment"),
        DAILY_BUDGET("daily_budget"),
        SAVING_PACE("saving_pace");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }

    // 선언 순서가 정렬 순서다 (danger → warn → good → info)
    public enum Severity {
        DANGER("danger"),
        WARN("warn"),
        GOOD("good"),
        INFO("info");

        private final String value;

        Severity(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }
}
