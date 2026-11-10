package com.dothis.fintris.devtools;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

// 시연용 가짜 거래 파일 (src/main/resources/demo). 실제 사람·계좌가 아니다
// 대학생 1명의 3개월 거래와 분류 결과(카테고리·소비 제외·확인 필요)가 들어 있다
final class DemoTransactionFile {

    static final String PATH = "demo/demo-3months-classified.json";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private DemoTransactionFile() {
    }

    record DemoTransaction(long id, LocalDateTime occurredAt, long amount, String direction, String merchantOrPayee,
                           String merchantType, String categoryCode, BigDecimal confidence, String status,
                           boolean excluded, boolean canceled) {
    }

    static List<DemoTransaction> read(ObjectMapper objectMapper, InputStream input) {
        JsonNode root = objectMapper.readTree(input);
        List<DemoTransaction> transactions = new ArrayList<>();
        for (JsonNode node : root.path("transactions")) {
            transactions.add(new DemoTransaction(
                    node.path("transactionId").asLong(),
                    OffsetDateTime.parse(node.path("occurredAt").asString()).atZoneSameInstant(KST).toLocalDateTime(),
                    node.path("amount").asLong(),
                    node.path("direction").asString(),
                    text(node, "merchantOrPayee"),
                    text(node, "merchantType"),
                    text(node, "categoryCode"),
                    node.path("confidence").isNumber() ? node.path("confidence").decimalValue() : null,
                    node.path("status").asString("CONFIRMED"),
                    node.path("excluded").asBoolean(false),
                    node.path("isCanceled").asBoolean(false)));
        }
        return transactions;
    }

    static List<DemoTransaction> readClasspath(ObjectMapper objectMapper) {
        try (InputStream input = DemoTransactionFile.class.getClassLoader().getResourceAsStream(PATH)) {
            if (input == null) {
                throw new IllegalStateException("시연 거래 파일이 없습니다: " + PATH);
            }
            return read(objectMapper, input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString() : null;
    }
}
