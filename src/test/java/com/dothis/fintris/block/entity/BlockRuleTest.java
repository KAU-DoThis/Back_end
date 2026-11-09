package com.dothis.fintris.block.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockRuleTest {

    // Hibernate가 JSON 컬럼을 읽을 때와 같은 Jackson 2 기본 설정
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 예전_snake_case로_저장된_규칙도_읽는다() throws Exception {
        String legacy = """
                {"pay_day": 25, "pay_amount": 600000, "monthly_target": 150000, "match_condition": "김OO"}
                """;

        BlockRule rule = objectMapper.readValue(legacy, BlockRule.class);

        assertThat(rule.payDay()).isEqualTo(25);
        assertThat(rule.payAmount()).isEqualTo(600_000L);
        assertThat(rule.monthlyTarget()).isEqualTo(150_000L);
        // 예전 문자열 매칭 조건은 받는 분(payee)으로 옮긴다
        assertThat(rule.matchCondition().payee()).isEqualTo("김OO");
        assertThat(rule.matchCondition().amountMode()).isNull();
    }

    @Test
    void 예전_규칙을_읽어_다시_저장해도_받는_분이_남는다() throws Exception {
        BlockRule legacy = objectMapper.readValue("{\"pay_day\": 25, \"match_condition\": \"김OO\"}", BlockRule.class);

        BlockRule merged = legacy.mergedWith(new BlockRule(null, null, null, 26, null, null, null));
        BlockRule reloaded = objectMapper.readValue(objectMapper.writeValueAsString(merged), BlockRule.class);

        assertThat(reloaded.payDay()).isEqualTo(26);
        assertThat(reloaded.matchCondition().payee()).isEqualTo("김OO");
    }

    @Test
    void 요청_본문은_Jackson_3에서도_객체와_예전_문자열을_모두_읽는다() {
        tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();

        BlockRule fromObject = mapper.readValue(
                "{\"payDay\": 25, \"matchCondition\": {\"payee\": \"김*수\", \"amountMode\": \"RANGE\", \"minAmount\": 540000}}",
                BlockRule.class);
        BlockRule fromLegacy = mapper.readValue("{\"match_condition\": \"김OO\"}", BlockRule.class);

        assertThat(fromObject.matchCondition().amountMode()).isEqualTo(BlockRule.AmountMode.RANGE);
        assertThat(fromObject.matchCondition().minAmount()).isEqualTo(540_000L);
        assertThat(fromLegacy.matchCondition().payee()).isEqualTo("김OO");
    }

    @Test
    void 새_규칙은_camelCase로_저장한다() throws Exception {
        BlockRule rule = new BlockRule(null, null, null, 25, 600_000L, null, null);

        String json = objectMapper.writeValueAsString(rule);

        assertThat(json).contains("\"payDay\":25").doesNotContain("pay_day")
                // 해당 유형에 없는 키는 내보내지 않는다
                .doesNotContain("monthlyAmount");
    }

    @Test
    void 수정할_때는_보낸_키만_바꾼다() {
        BlockRule saved = new BlockRule(null, null, null, 25, 600_000L,
                new BlockRule.MatchCondition("김*수", BlockRule.AmountMode.EXACT, 600_000L, 600_000L, 3, 3L),
                new BlockRule.Alerts(true, true, false));
        BlockRule patch = new BlockRule(null, null, null, null, null, null,
                new BlockRule.Alerts(null, null, true));

        BlockRule merged = saved.mergedWith(patch);

        assertThat(merged.payDay()).isEqualTo(25);
        assertThat(merged.matchCondition().payee()).isEqualTo("김*수");
        assertThat(merged.alerts()).isEqualTo(new BlockRule.Alerts(true, true, true));
    }

    @Test
    void 납부_확인_조건은_통째로_바꾼다() {
        BlockRule saved = new BlockRule(null, null, null, 25, 600_000L,
                new BlockRule.MatchCondition("김*수", BlockRule.AmountMode.EXACT, 600_000L, 600_000L, 3, 3L), null);
        BlockRule.MatchCondition range =
                new BlockRule.MatchCondition("김*수", BlockRule.AmountMode.RANGE, 540_000L, 660_000L, null, null);

        BlockRule merged = saved.mergedWith(new BlockRule(null, null, null, null, null, range, null));

        assertThat(merged.matchCondition()).isEqualTo(range);
    }
}
