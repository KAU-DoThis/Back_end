package com.dothis.fintris.codef.client;

import com.dothis.fintris.codef.client.CodefApiClient.CodefResponse;
import com.dothis.fintris.codef.client.CodefApiClient.CodefResult;
import com.dothis.fintris.codef.client.CodefBankTransactionClient.BankTransaction;
import com.dothis.fintris.codef.client.CodefBankTransactionClient.BankTransactionsResult;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodefBankTransactionClientTest {

    private static final String PATH = "/v1/kr/bank/p/account/transaction-list";
    private static final LocalDate START = LocalDate.of(2026, 7, 28);
    private static final LocalDate END = LocalDate.of(2026, 10, 28);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock CodefApiClient codefApiClient;
    @InjectMocks CodefBankTransactionClient client;

    private void givenSuccess(String dataJson) {
        when(codefApiClient.post(eq(PATH), any())).thenReturn(
                new CodefResponse(new CodefResult("CF-00000", "성공", null, null), jsonMapper.readTree(dataJson)));
    }

    private BankTransactionsResult fetch() {
        return client.fetchTransactions("cid-1", "0004", "100-244-000123", START, END);
    }

    @Test
    @DisplayName("계좌번호는 숫자만, 날짜는 yyyyMMdd로 과거순 조회를 요청한다")
    void requestsWithDigitsOnlyAccountAndAscendingOrder() {
        givenSuccess("{}");

        fetch();

        ArgumentCaptor<Object> body = ArgumentCaptor.captor();
        verify(codefApiClient).post(eq(PATH), body.capture());
        assertThat(body.getValue()).isEqualTo(Map.of(
                "connectedId", "cid-1", "organization", "0004", "account", "100244000123",
                "startDate", "20260728", "endDate", "20261028", "orderBy", "1", "inquiryType", "0"));
    }

    @Test
    @DisplayName("출금이면 OUT, 입금이면 IN으로 금액과 거래 후 잔액을 읽는다")
    void parsesDirectionAmountAndBalance() {
        givenSuccess("""
                {"resTrHistoryList": [
                  {"resAccountTrDate": "20261001", "resAccountTrTime": "093015", "resAccountOut": "4,500",
                   "resAccountIn": "0", "resAfterTranBalance": "120000", "resAccountDesc1": "스타벅스"},
                  {"resAccountTrDate": "20261002", "resAccountTrTime": "180000", "resAccountOut": "0",
                   "resAccountIn": "300000", "resAfterTranBalance": "420000", "resAccountDesc3": "급여"}
                ]}
                """);

        BankTransactionsResult result = fetch();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.transactions()).containsExactly(
                new BankTransaction(LocalDateTime.of(2026, 10, 1, 9, 30, 15), TransactionDirection.OUT, 4_500L,
                        120_000L, "스타벅스", null, null, null),
                new BankTransaction(LocalDateTime.of(2026, 10, 2, 18, 0, 0), TransactionDirection.IN, 300_000L,
                        420_000L, null, null, "급여", null));
    }

    @Test
    @DisplayName("거래가 하나면 배열이 아니라 객체로 와도 읽는다")
    void parsesSingleObject() {
        givenSuccess("""
                {"resTrHistoryList": {"resAccountTrDate": "20261001", "resAccountTrTime": "000219",
                 "resAccountOut": "0", "resAccountIn": "1", "resAfterTranBalance": "11517"}}
                """);

        assertThat(fetch().transactions()).hasSize(1);
    }

    @Test
    @DisplayName("거래시각이 비어 있으면 자정으로 둔다")
    void blankTimeBecomesMidnight() {
        givenSuccess("""
                {"resTrHistoryList": [{"resAccountTrDate": "20261001", "resAccountTrTime": "",
                 "resAccountOut": "1000", "resAccountIn": "0", "resAfterTranBalance": ""}]}
                """);

        BankTransaction transaction = fetch().transactions().get(0);

        assertThat(transaction.occurredAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(transaction.balanceAfter()).isNull();
    }

    @Test
    @DisplayName("금액이 0이거나 일자를 읽을 수 없는 행은 버린다")
    void skipsZeroAmountAndInvalidDate() {
        givenSuccess("""
                {"resTrHistoryList": [
                  {"resAccountTrDate": "20261001", "resAccountTrTime": "010101", "resAccountOut": "0", "resAccountIn": "0"},
                  {"resAccountTrDate": "", "resAccountTrTime": "010101", "resAccountOut": "500", "resAccountIn": "0"},
                  {"resAccountTrDate": "2026-10-01", "resAccountTrTime": "010101", "resAccountOut": "500", "resAccountIn": "0"}
                ]}
                """);

        assertThat(fetch().transactions()).isEmpty();
    }

    @Test
    @DisplayName("거래 목록이 없으면 빈 목록으로 성공한다")
    void emptyHistoryIsSuccess() {
        givenSuccess("{}");

        BankTransactionsResult result = fetch();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.transactions()).isEmpty();
    }

    @Test
    @DisplayName("CODEF가 실패를 돌려주면 코드와 메시지를 그대로 전달한다")
    void deliversFailure() {
        when(codefApiClient.post(eq(PATH), any())).thenReturn(
                new CodefResponse(new CodefResult("CF-12801", "조회에 실패했습니다.", null, null), null));

        BankTransactionsResult result = fetch();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureCode()).isEqualTo("CF-12801");
        assertThat(result.failureMessage()).isEqualTo("조회에 실패했습니다.");
        assertThat(result.transactions()).isEmpty();
    }
}
