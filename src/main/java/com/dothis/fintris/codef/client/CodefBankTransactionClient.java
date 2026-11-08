package com.dothis.fintris.codef.client;

import com.dothis.fintris.codef.client.CodefApiClient.CodefResponse;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CodefBankTransactionClient {

    private static final String TRANSACTION_LIST_PATH = "/v1/kr/bank/p/account/transaction-list";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");

    private final CodefApiClient codefApiClient;

    // 은행이 허용하는 기간보다 과거인 시작일은 CODEF가 허용 범위로 맞춰 조회한다
    public BankTransactionsResult fetchTransactions(String connectedId, String organization, String accountNumber,
                                                    LocalDate startDate, LocalDate endDate) {
        CodefResponse response = codefApiClient.post(TRANSACTION_LIST_PATH, Map.of(
                "connectedId", connectedId,
                "organization", organization,
                "account", accountNumber.replaceAll("\\D", ""),
                "startDate", startDate.format(DATE),
                "endDate", endDate.format(DATE),
                "orderBy", "1", // 과거순
                "inquiryType", "0")); // 계좌 상세는 필요 없다
        if (!response.isSuccess()) {
            return BankTransactionsResult.failure(response.result().code(), response.result().message());
        }

        List<BankTransaction> transactions = new ArrayList<>();
        JsonNode history = response.data() == null ? null : response.data().path("resTrHistoryList");
        // 거래가 하나면 배열이 아니라 객체로 온다
        if (history != null && history.isObject()) {
            addIfValid(transactions, history);
        } else if (history != null && history.isArray()) {
            for (JsonNode item : history) {
                addIfValid(transactions, item);
            }
        }
        // 거래가 없는 기간에도 현재 잔액은 응답에 실려 온다
        Long currentBalance = response.data() == null ? null : parseBalance(text(response.data(), "resAccountBalance"));
        return BankTransactionsResult.success(transactions, currentBalance);
    }

    private void addIfValid(List<BankTransaction> transactions, JsonNode item) {
        LocalDateTime occurredAt = parseDateTime(text(item, "resAccountTrDate"), text(item, "resAccountTrTime"));
        long out = parseWon(text(item, "resAccountOut"));
        long in = parseWon(text(item, "resAccountIn"));
        // 일시를 모르거나 금액이 0인 행은 가계부에 쓸 수 없어 버린다
        if (occurredAt == null || (out == 0 && in == 0)) {
            return;
        }
        boolean isOut = out > 0;
        String balance = text(item, "resAfterTranBalance");
        transactions.add(new BankTransaction(occurredAt,
                isOut ? TransactionDirection.OUT : TransactionDirection.IN,
                isOut ? out : in,
                balance == null ? null : parseWon(balance),
                text(item, "resAccountDesc1"), text(item, "resAccountDesc2"),
                text(item, "resAccountDesc3"), text(item, "resAccountDesc4")));
    }

    // 거래시각이 비어 있는 은행이 있어 그때는 자정으로 둔다
    private LocalDateTime parseDateTime(String date, String time) {
        if (date == null) {
            return null;
        }
        try {
            LocalTime localTime = time == null ? LocalTime.MIDNIGHT : LocalTime.parse(time, TIME);
            return LocalDateTime.of(LocalDate.parse(date, DATE), localTime);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    // 금액은 문자열로 오고 비어 있을 수 있다. 원 단위 정수로만 받고 읽을 수 없으면 0이다
    private long parseWon(String amount) {
        if (amount == null) {
            return 0;
        }
        String digits = amount.replace(",", "");
        int dot = digits.indexOf('.');
        try {
            return Long.parseLong(dot < 0 ? digits : digits.substring(0, dot));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // 비었거나 읽을 수 없으면 0이 아니라 null이다. 잔액 0원과 구분해야 한다
    private Long parseBalance(String balance) {
        if (balance == null) {
            return null;
        }
        String digits = balance.replace(",", "");
        int dot = digits.indexOf('.');
        try {
            return Long.parseLong(dot < 0 ? digits : digits.substring(0, dot));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // desc1~4는 은행마다 채우는 방식이 달라 그대로 넘기고, 가맹점·수취인 선택은 사용하는 쪽에서 한다
    public record BankTransaction(LocalDateTime occurredAt, TransactionDirection direction, long amount,
                                  Long balanceAfter, String desc1, String desc2, String desc3, String desc4) {
    }

    public record BankTransactionsResult(List<BankTransaction> transactions, Long currentBalance,
                                         String failureCode, String failureMessage) {

        static BankTransactionsResult success(List<BankTransaction> transactions, Long currentBalance) {
            return new BankTransactionsResult(transactions, currentBalance, null, null);
        }

        static BankTransactionsResult failure(String code, String message) {
            return new BankTransactionsResult(List.of(), null, code, message);
        }

        public boolean isSuccess() {
            return failureCode == null;
        }
    }
}
