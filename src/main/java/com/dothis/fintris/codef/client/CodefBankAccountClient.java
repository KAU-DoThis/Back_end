package com.dothis.fintris.codef.client;

import com.dothis.fintris.codef.client.CodefApiClient.CodefResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CodefBankAccountClient {

    private static final String ACCOUNT_LIST_PATH = "/v1/kr/bank/p/account/account-list";
    // 거래내역 API가 수시입출 계좌만 지원하므로 10(미분류)·11(수시입출)만 다룬다
    private static final Set<String> DEMAND_DEPOSIT_TYPES = Set.of("10", "11");

    private final CodefApiClient codefApiClient;

    public BankAccountsResult fetchDemandDepositAccounts(String connectedId, String organization) {
        CodefResponse response = codefApiClient.post(ACCOUNT_LIST_PATH,
                Map.of("connectedId", connectedId, "organization", organization));
        if (!response.isSuccess()) {
            return BankAccountsResult.failure(response.result().code(), response.result().message());
        }

        List<BankAccount> accounts = new ArrayList<>();
        JsonNode deposits = response.data() == null ? null : response.data().path("resDepositTrust");
        // 계좌가 하나면 배열이 아니라 객체로 온다
        if (deposits != null && deposits.isObject()) {
            addIfDemandDeposit(accounts, deposits);
        } else if (deposits != null && deposits.isArray()) {
            for (JsonNode deposit : deposits) {
                addIfDemandDeposit(accounts, deposit);
            }
        }
        return BankAccountsResult.success(accounts);
    }

    private void addIfDemandDeposit(List<BankAccount> accounts, JsonNode deposit) {
        String accountNumber = text(deposit, "resAccount");
        String depositType = text(deposit, "resAccountDeposit");
        // Set.of는 contains(null)에서 예외를 던지므로 null을 먼저 거른다
        if (accountNumber == null || depositType == null || !DEMAND_DEPOSIT_TYPES.contains(depositType)) {
            return;
        }
        accounts.add(new BankAccount(accountNumber, text(deposit, "resAccountName"),
                parseWon(text(deposit, "resAccountBalance"))));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    // 금액은 문자열로 오고 비어 있을 수 있다. 원 단위 정수로만 받는다
    private Long parseWon(String amount) {
        if (amount == null) {
            return null;
        }
        String digits = amount.replace(",", "");
        int dot = digits.indexOf('.');
        try {
            return Long.parseLong(dot < 0 ? digits : digits.substring(0, dot));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record BankAccount(String accountNumber, String accountName, Long balance) {
    }

    public record BankAccountsResult(List<BankAccount> accounts, String failureCode, String failureMessage) {

        static BankAccountsResult success(List<BankAccount> accounts) {
            return new BankAccountsResult(accounts, null, null);
        }

        static BankAccountsResult failure(String code, String message) {
            return new BankAccountsResult(List.of(), code, message);
        }

        public boolean isSuccess() {
            return failureCode == null;
        }
    }
}
