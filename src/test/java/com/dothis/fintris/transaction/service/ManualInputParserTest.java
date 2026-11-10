package com.dothis.fintris.transaction.service;

import com.dothis.fintris.transaction.service.ManualInputParser.Parsed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ManualInputParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "붕어빵 3000          | 3000  | 붕어빵",
            "붕어빵 3,000원       | 3000  | 붕어빵",
            "김밥 8천원           | 8000  | 김밥",
            "택시 1만2천원        | 12000 | 택시",
            "치킨 1.5만           | 15000 | 치킨",
            "3000 편의점          | 3000  | 편의점",
            "2호선 카페 4500      | 4500  | 2호선 카페",
    })
    @DisplayName("금액 표현(숫자·쉼표·천·만)을 읽고 나머지를 내용으로 남긴다")
    void parsesAmount(String text, long amount, String description) {
        Parsed parsed = ManualInputParser.parse(text, TODAY).orElseThrow();

        assertThat(parsed.amount()).isEqualTo(amount);
        assertThat(parsed.description()).isEqualTo(description);
        assertThat(parsed.occurredDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("어제·그저께가 있으면 날짜를 바꾸고 내용에서 뺀다")
    void parsesRelativeDate() {
        assertThat(ManualInputParser.parse("어제 택시 9000", TODAY).orElseThrow())
                .isEqualTo(new Parsed(9000, "택시", TODAY.minusDays(1)));
        assertThat(ManualInputParser.parse("그저께 영화 15000", TODAY).orElseThrow().occurredDate())
                .isEqualTo(TODAY.minusDays(2));
    }

    @Test
    @DisplayName("금액만 있으면 내용은 비운다")
    void amountOnly() {
        assertThat(ManualInputParser.parse("5000원", TODAY).orElseThrow())
                .isEqualTo(new Parsed(5000, null, TODAY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "붕어빵", "공짜 0원", "집 2억"})
    @DisplayName("금액을 못 찾거나 0원·1억 원 초과면 해석 실패")
    void fails(String text) {
        assertThat(ManualInputParser.parse(text, TODAY)).isEmpty();
    }
}
