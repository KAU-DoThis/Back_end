package com.dothis.fintris.codef.client;

import com.dothis.fintris.codef.client.CodefApiClient.CodefResponse;
import com.dothis.fintris.codef.client.CodefApiClient.CodefResult;
import com.dothis.fintris.codef.client.CodefBankAccountClient.BankAccount;
import com.dothis.fintris.codef.client.CodefBankAccountClient.BankAccountsResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodefBankAccountClientTest {

    private static final String PATH = "/v1/kr/bank/p/account/account-list";
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock CodefApiClient codefApiClient;
    @InjectMocks CodefBankAccountClient client;

    private void givenSuccess(String dataJson) {
        when(codefApiClient.post(eq(PATH), any())).thenReturn(
                new CodefResponse(new CodefResult("CF-00000", "성공", null, null), jsonMapper.readTree(dataJson)));
    }

    @Test
    @DisplayName("연결 정보와 기관코드로 보유계좌 조회를 요청한다")
    void requestsAccountListWithConnectedIdAndOrganization() {
        givenSuccess("{}");

        client.fetchDemandDepositAccounts("cid-1", "0004");

        ArgumentCaptor<Object> body = ArgumentCaptor.captor();
        verify(codefApiClient).post(eq(PATH), body.capture());
        assertThat(body.getValue()).isEqualTo(Map.of("connectedId", "cid-1", "organization", "0004"));
    }

    @Test
    @DisplayName("계좌가 하나면 배열이 아니라 객체로 와도 읽는다")
    void parsesSingleObject() {
        givenSuccess("""
                {"resDepositTrust": {"resAccount": "02004200000000", "resAccountName": "입출금통장",
                 "resAccountBalance": "546", "resAccountDeposit": "11"}}
                """);

        BankAccountsResult result = client.fetchDemandDepositAccounts("cid-1", "0020");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.accounts()).containsExactly(new BankAccount("02004200000000", "입출금통장", 546L));
    }

    @Test
    @DisplayName("수시입출(11)과 미분류(10)만 남기고 정기·신탁·구분 없는 계좌는 거른다")
    void keepsOnlyDemandDepositTypes() {
        givenSuccess("""
                {"resDepositTrust": [
                  {"resAccount": "111", "resAccountName": "미분류", "resAccountBalance": "1", "resAccountDeposit": "10"},
                  {"resAccount": "222", "resAccountName": "수시입출", "resAccountBalance": "2", "resAccountDeposit": "11"},
                  {"resAccount": "333", "resAccountName": "적금", "resAccountBalance": "3", "resAccountDeposit": "12"},
                  {"resAccount": "444", "resAccountName": "신탁", "resAccountBalance": "4", "resAccountDeposit": "13"},
                  {"resAccount": "555", "resAccountName": "구분없음", "resAccountBalance": "5", "resAccountDeposit": ""}
                ]}
                """);

        BankAccountsResult result = client.fetchDemandDepositAccounts("cid-1", "0004");

        assertThat(result.accounts()).extracting(BankAccount::accountNumber).containsExactly("111", "222");
    }

    @Test
    @DisplayName("예금구분 필드가 아예 없어도 예외 없이 건너뛴다")
    void skipsAccountWithoutDepositType() {
        givenSuccess("""
                {"resDepositTrust": [{"resAccount": "111", "resAccountName": "이름만", "resAccountBalance": "1"}]}
                """);

        assertThat(client.fetchDemandDepositAccounts("cid-1", "0004").accounts()).isEmpty();
    }

    @Test
    @DisplayName("외화·펀드·대출·보험 항목은 무시한다")
    void ignoresOtherProductTypes() {
        givenSuccess("""
                {"resForeignCurrency": [{"resAccount": "f1", "resAccountDeposit": "20"}],
                 "resFund": [{"resAccount": "f2", "resAccountDeposit": "30"}],
                 "resLoan": [{"resAccount": "f3", "resAccountDeposit": "40"}],
                 "resInsurance": [{"resAccount": "f4", "resAccountDeposit": "50"}]}
                """);

        BankAccountsResult result = client.fetchDemandDepositAccounts("cid-1", "0004");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.accounts()).isEmpty();
    }

    @Test
    @DisplayName("잔액은 빈 값이면 null, 쉼표·소수점이 있어도 원 단위 정수로 바꾼다")
    void parsesBalanceAsWon() {
        givenSuccess("""
                {"resDepositTrust": [
                  {"resAccount": "1", "resAccountBalance": "", "resAccountDeposit": "11"},
                  {"resAccount": "2", "resAccountBalance": "1,250,000", "resAccountDeposit": "11"},
                  {"resAccount": "3", "resAccountBalance": "1250.00", "resAccountDeposit": "11"},
                  {"resAccount": "4", "resAccountBalance": "abc", "resAccountDeposit": "11"},
                  {"resAccount": "5", "resAccountBalance": "-300", "resAccountDeposit": "11"}
                ]}
                """);

        BankAccountsResult result = client.fetchDemandDepositAccounts("cid-1", "0004");

        assertThat(result.accounts()).extracting(BankAccount::balance)
                .containsExactly(null, 1_250_000L, 1_250L, null, -300L);
    }

    @Test
    @DisplayName("기관 오류는 예외 없이 코드와 메시지로 돌려준다")
    void returnsFailureCodeAndMessage() {
        when(codefApiClient.post(eq(PATH), any())).thenReturn(
                new CodefResponse(new CodefResult("CF-02100", "조회 계좌로 등록된 계좌가 없습니다.", null, null), null));

        BankAccountsResult result = client.fetchDemandDepositAccounts("cid-1", "0011");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureCode()).isEqualTo("CF-02100");
        assertThat(result.failureMessage()).isEqualTo("조회 계좌로 등록된 계좌가 없습니다.");
        assertThat(result.accounts()).isEmpty();
    }
}
