package com.dothis.fintris.codef.client;

import com.dothis.fintris.codef.client.CodefApiClient.CodefResponse;
import com.dothis.fintris.codef.client.CodefApiClient.CodefResult;
import com.dothis.fintris.codef.client.CodefCardClient.Card;
import com.dothis.fintris.codef.client.CodefCardClient.CardsResult;
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
class CodefCardClientTest {

    private static final String PATH = "/v1/kr/card/p/account/card-list";
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock CodefApiClient codefApiClient;
    @InjectMocks CodefCardClient client;

    private void givenSuccess(String dataJson) {
        when(codefApiClient.post(eq(PATH), any())).thenReturn(
                new CodefResponse(new CodefResult("CF-00000", "성공", null, null), jsonMapper.readTree(dataJson)));
    }

    @Test
    @DisplayName("연결 정보, 기관코드, 생년월일로 보유카드 조회를 요청한다")
    void requestsCardList() {
        givenSuccess("[]");

        client.fetchCards("cid-1", "0306", "980225");

        ArgumentCaptor<Object> body = ArgumentCaptor.captor();
        verify(codefApiClient).post(eq(PATH), body.capture());
        assertThat(body.getValue())
                .isEqualTo(Map.of("connectedId", "cid-1", "organization", "0306", "birthDate", "980225"));
    }

    @Test
    @DisplayName("카드 여러 장은 배열로 읽고 가려진 카드번호를 그대로 쓴다")
    void parsesCardArray() {
        givenSuccess("""
                [{"resCardType": "체크/본인", "resCardName": "OO체크카드", "resCardNo": "6056********0000"},
                 {"resCardType": "신용/본인", "resCardName": "할인카드", "resCardNo": "6253********1111"}]
                """);

        CardsResult result = client.fetchCards("cid-1", "0306", "980225");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.cards()).containsExactly(
                new Card("6056********0000", "OO체크카드", "체크/본인"),
                new Card("6253********1111", "할인카드", "신용/본인"));
    }

    @Test
    @DisplayName("카드가 하나면 객체로 와도 읽는다")
    void parsesSingleObject() {
        givenSuccess("{\"resCardType\": \"신용/본인\", \"resCardName\": \"할인카드\", \"resCardNo\": \"6253********1111\"}");

        assertThat(client.fetchCards("cid-1", "0306", "980225").cards())
                .containsExactly(new Card("6253********1111", "할인카드", "신용/본인"));
    }

    @Test
    @DisplayName("카드번호가 없는 항목은 건너뛰고, 이름·종류가 비어도 읽는다")
    void skipsItemsWithoutCardNumber() {
        givenSuccess("""
                [{"resCardName": "번호없음", "resCardNo": ""},
                 {"resCardName": "", "resCardType": "", "resCardNo": "6253********1111"}]
                """);

        assertThat(client.fetchCards("cid-1", "0306", "980225").cards())
                .containsExactly(new Card("6253********1111", null, null));
    }

    @Test
    @DisplayName("data가 비어 있으면 카드 0장으로 성공 처리한다")
    void emptyDataIsSuccessWithNoCards() {
        when(codefApiClient.post(eq(PATH), any())).thenReturn(
                new CodefResponse(new CodefResult("CF-00000", "성공", null, null), null));

        CardsResult result = client.fetchCards("cid-1", "0306", "980225");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.cards()).isEmpty();
    }

    @Test
    @DisplayName("기관 오류는 예외 없이 코드와 메시지로 돌려준다")
    void returnsFailureCodeAndMessage() {
        when(codefApiClient.post(eq(PATH), any())).thenReturn(
                new CodefResponse(new CodefResult("CF-12801", "조회에 실패했습니다.", null, null), null));

        CardsResult result = client.fetchCards("cid-1", "0306", "980225");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureCode()).isEqualTo("CF-12801");
        assertThat(result.failureMessage()).isEqualTo("조회에 실패했습니다.");
        assertThat(result.cards()).isEmpty();
    }
}
