package com.dothis.fintris.devtools;

import com.dothis.fintris.devtools.DemoTransactionFile.DemoTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoTransactionFileTest {

    private final List<DemoTransaction> demo = DemoTransactionFile.readClasspath(JsonMapper.builder().build());

    @Test
    @DisplayName("시연 파일의 거래 197건을 모두 읽는다")
    void readsAll() {
        assertThat(demo).hasSize(197);
        assertThat(demo.stream().map(DemoTransaction::id).distinct()).hasSize(197);
    }

    @Test
    @DisplayName("일시는 한국 시간으로, 분류 결과(카테고리·소비 제외·확인 필요)는 그대로 읽는다")
    void readsFields() {
        DemoTransaction first = demo.getFirst();
        assertThat(first.occurredAt()).isEqualTo(LocalDateTime.of(2026, 8, 12, 21, 18));
        assertThat(first.merchantOrPayee()).isEqualTo("쿠팡이츠");
        assertThat(first.categoryCode()).isEqualTo("DELIVERY_DINING");

        assertThat(demo.stream().filter(DemoTransaction::excluded)).hasSize(14);
        assertThat(demo.stream().filter(transaction -> "NEEDS_REVIEW".equals(transaction.status()))).hasSize(4);
        assertThat(demo.stream().filter(transaction -> "IN".equals(transaction.direction()))).hasSize(10);
    }
}
