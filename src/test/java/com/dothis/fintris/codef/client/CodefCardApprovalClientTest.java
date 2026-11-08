package com.dothis.fintris.codef.client;

import com.dothis.fintris.codef.client.CodefApiClient.CodefResponse;
import com.dothis.fintris.codef.client.CodefApiClient.CodefResult;
import com.dothis.fintris.codef.client.CodefCardApprovalClient.ApprovalStatus;
import com.dothis.fintris.codef.client.CodefCardApprovalClient.CardApproval;
import com.dothis.fintris.codef.client.CodefCardApprovalClient.CardApprovalsResult;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodefCardApprovalClientTest {

    private static final String PATH = "/v1/kr/card/p/account/approval-list";
    private static final LocalDate START = LocalDate.of(2026, 8, 8);
    private static final LocalDate END = LocalDate.of(2026, 11, 8);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock CodefApiClient codefApiClient;
    @InjectMocks CodefCardApprovalClient client;

    private void givenSuccess(String dataJson) {
        when(codefApiClient.post(eq(PATH), any())).thenReturn(
                new CodefResponse(new CodefResult("CF-00000", "성공", null, null), jsonMapper.readTree(dataJson)));
    }

    private CardApprovalsResult fetch() {
        return client.fetchApprovals("cid-1", "0306", START, END);
    }

    @Test
    @DisplayName("카드 전체를 과거순으로, 가맹점 정보를 포함해 요청한다")
    void requestsAllCardsWithMerchantInfo() {
        givenSuccess("[]");

        fetch();

        ArgumentCaptor<Object> body = ArgumentCaptor.captor();
        verify(codefApiClient).post(eq(PATH), body.capture());
        assertThat(body.getValue()).isEqualTo(Map.of(
                "connectedId", "cid-1", "organization", "0306", "startDate", "20260808", "endDate", "20261108",
                "orderBy", "1", "inquiryType", "1", "memberStoreInfoType", "1"));
    }

    @Test
    @DisplayName("승인일시·카드번호·승인번호·금액·가맹점명·업종을 읽는다")
    void parsesApproval() {
        givenSuccess("""
                [{"resUsedDate": "20261107", "resUsedTime": "093015", "resCardNo": "6056********1234",
                  "resApprovalNo": "30012345", "resUsedAmount": "4,500", "resCancelYN": "0",
                  "resMemberStoreName": "스타벅스 강남점", "resMemberStoreType": "커피전문점"}]
                """);

        CardApprovalsResult result = fetch();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.approvals()).containsExactly(new CardApproval(LocalDateTime.of(2026, 11, 7, 9, 30, 15),
                "6056********1234", "30012345", 4_500L, ApprovalStatus.APPROVED, 0L, "스타벅스 강남점", "커피전문점"));
    }

    @Test
    @DisplayName("승인이 하나면 객체로 와도 읽는다")
    void parsesSingleObject() {
        givenSuccess("""
                {"resUsedDate": "20261107", "resUsedTime": "093015", "resUsedAmount": "4500"}
                """);

        assertThat(fetch().approvals()).hasSize(1);
    }

    @Test
    @DisplayName("취소·부분취소·거절 상태와 취소 금액을 읽는다")
    void parsesCancelStatus() {
        givenSuccess("""
                [{"resUsedDate": "20261107", "resUsedTime": "100000", "resUsedAmount": "10000", "resCancelYN": "1"},
                 {"resUsedDate": "20261107", "resUsedTime": "110000", "resUsedAmount": "10000", "resCancelYN": "2",
                  "resCancelAmount": "3,000"},
                 {"resUsedDate": "20261107", "resUsedTime": "120000", "resUsedAmount": "10000", "resCancelYN": "3"}]
                """);

        assertThat(fetch().approvals())
                .extracting(CardApproval::status, CardApproval::cancelAmount)
                .containsExactly(
                        tuple(ApprovalStatus.CANCELED, 0L),
                        tuple(ApprovalStatus.PARTIAL_CANCELED, 3_000L),
                        tuple(ApprovalStatus.REJECTED, 0L));
    }

    @Test
    @DisplayName("음수 금액(KB 취소전표)은 취소로 보고 금액은 양수로 둔다")
    void negativeAmountIsCancel() {
        givenSuccess("""
                [{"resUsedDate": "20261107", "resUsedTime": "100000", "resUsedAmount": "-10,000", "resCancelYN": "0"}]
                """);

        CardApproval approval = fetch().approvals().getFirst();
        assertThat(approval.status()).isEqualTo(ApprovalStatus.CANCELED);
        assertThat(approval.amount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("일시나 금액을 읽을 수 없거나 0원인 행은 버리고, 시각이 없으면 자정으로 둔다")
    void skipsUnusableRows() {
        givenSuccess("""
                [{"resUsedTime": "100000", "resUsedAmount": "1000"},
                 {"resUsedDate": "20261107", "resUsedAmount": "N/A"},
                 {"resUsedDate": "20261107", "resUsedAmount": "0"},
                 {"resUsedDate": "20261107", "resUsedAmount": "2000"}]
                """);

        assertThat(fetch().approvals()).extracting(CardApproval::occurredAt, CardApproval::amount)
                .containsExactly(tuple(LocalDateTime.of(2026, 11, 7, 0, 0), 2_000L));
    }

    @Test
    @DisplayName("CODEF가 거절하면 실패 코드와 메시지를 돌려준다")
    void deliversFailure() {
        when(codefApiClient.post(eq(PATH), any())).thenReturn(
                new CodefResponse(new CodefResult("CF-12801", "조회에 실패했습니다.", null, null), null));

        CardApprovalsResult result = fetch();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureCode()).isEqualTo("CF-12801");
        assertThat(result.approvals()).isEmpty();
    }
}
