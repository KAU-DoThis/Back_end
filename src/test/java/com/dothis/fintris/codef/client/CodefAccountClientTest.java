package com.dothis.fintris.codef.client;

import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.codef.client.CodefAccountClient.CodefAccountResult;
import com.dothis.fintris.codef.client.CodefApiClient.CodefResponse;
import com.dothis.fintris.codef.client.CodefApiClient.CodefResult;
import com.dothis.fintris.global.crypto.RsaEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodefAccountClientTest {

    private static final String CREATE_PATH = "/v1/account/create";
    private static final String ADD_PATH = "/v1/account/add";
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock CodefApiClient codefApiClient;
    @Mock RsaEncryptor rsaEncryptor;
    @InjectMocks CodefAccountClient client;

    private CodefResponse response(String code, String message, String dataJson) {
        return new CodefResponse(new CodefResult(code, message, null, null),
                dataJson == null ? null : jsonMapper.readTree(dataJson));
    }

    private void givenResponse(CodefResponse response) {
        when(rsaEncryptor.encrypt("pw1234")).thenReturn("ENCRYPTED");
        when(codefApiClient.post(anyString(), any())).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedBody(String expectedPath) {
        ArgumentCaptor<Object> body = ArgumentCaptor.captor();
        verify(codefApiClient).post(org.mockito.ArgumentMatchers.eq(expectedPath), body.capture());
        return (Map<String, Object>) body.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstAccount(Map<String, Object> body) {
        return ((List<Map<String, Object>>) body.get("accountList")).get(0);
    }

    @Test
    @DisplayName("connectedId가 없으면 create로 보내고 아이디 로그인 필드를 채운다")
    void createsWhenNoConnectedId() {
        givenResponse(response("CF-00000", "성공", "{\"connectedId\": \"cid-1\"}"));

        CodefAccountResult result = client.register(null, "0004", LinkedAccountType.BANK, "codef", "pw1234", "980225");

        Map<String, Object> body = capturedBody(CREATE_PATH);
        assertThat(body).doesNotContainKey("connectedId");
        assertThat(firstAccount(body)).containsEntry("countryCode", "KR")
                .containsEntry("businessType", "BK")
                .containsEntry("clientType", "P")
                .containsEntry("organization", "0004")
                .containsEntry("loginType", "1")
                .containsEntry("id", "codef")
                .containsEntry("birthDate", "980225");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.connectedId()).isEqualTo("cid-1");
    }

    @Test
    @DisplayName("비밀번호는 RSA로 암호화된 값만 보내고 평문은 어디에도 싣지 않는다")
    void sendsOnlyEncryptedPassword() {
        givenResponse(response("CF-00000", "성공", "{\"connectedId\": \"cid-1\"}"));

        client.register(null, "0004", LinkedAccountType.BANK, "codef", "pw1234", "980225");

        Map<String, Object> body = capturedBody(CREATE_PATH);
        assertThat(firstAccount(body)).containsEntry("password", "ENCRYPTED");
        assertThat(body.toString()).doesNotContain("pw1234");
    }

    @Test
    @DisplayName("connectedId가 있으면 add로 보내고 body에 connectedId를 싣는다")
    void addsWhenConnectedIdExists() {
        givenResponse(response("CF-00000", "성공", "{\"connectedId\": \"cid-1\"}"));

        client.register("cid-1", "0088", LinkedAccountType.BANK, "codef", "pw1234", "980225");

        Map<String, Object> body = capturedBody(ADD_PATH);
        assertThat(body).containsEntry("connectedId", "cid-1");
        assertThat(firstAccount(body)).containsEntry("organization", "0088");
    }

    @Test
    @DisplayName("add 응답에 connectedId가 없으면 요청에 쓴 값을 그대로 돌려준다")
    void addFallsBackToRequestedConnectedId() {
        givenResponse(response("CF-00000", "성공", "{\"successList\": [{\"code\": \"CF-00000\"}]}"));

        CodefAccountResult result = client.register("cid-1", "0088", LinkedAccountType.BANK, "codef", "pw1234", "980225");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.connectedId()).isEqualTo("cid-1");
    }

    @Test
    @DisplayName("카드는 businessType이 CD로 나간다")
    void cardUsesCardBusinessType() {
        givenResponse(response("CF-00000", "성공", "{\"connectedId\": \"cid-1\"}"));

        client.register(null, "0309", LinkedAccountType.CARD, "codef", "pw1234", "980225");

        assertThat(firstAccount(capturedBody(CREATE_PATH))).containsEntry("businessType", "CD");
    }

    @Test
    @DisplayName("등록이 실패하면 errorList 첫 항목의 코드와 메시지를 돌려준다")
    void returnsFirstErrorFromErrorList() {
        givenResponse(response("CF-04000", "일부 실패", """
                {"errorList": [{"code": "CF-12801", "message": "비밀번호가 일치하지 않습니다."}]}
                """));

        CodefAccountResult result = client.register(null, "0004", LinkedAccountType.BANK, "codef", "pw1234", "980225");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureCode()).isEqualTo("CF-12801");
        assertThat(result.failureMessage()).isEqualTo("비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("errorList가 없으면 result의 코드와 메시지를 돌려준다")
    void fallsBackToResultCodeWhenNoErrorList() {
        givenResponse(response("CF-03002", "추가 인증이 필요합니다.", null));

        CodefAccountResult result = client.register(null, "0027", LinkedAccountType.BANK, "codef", "pw1234", "980225");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureCode()).isEqualTo("CF-03002");
        assertThat(result.failureMessage()).isEqualTo("추가 인증이 필요합니다.");
    }

    @Test
    @DisplayName("삭제는 delete 경로로 기관 정보와 connectedId만 보내고 비밀번호는 싣지 않는다")
    void unregisterSendsInstitutionOnly() {
        when(codefApiClient.post(anyString(), any())).thenReturn(response("CF-00000", "성공", "{\"connectedId\": \"cid-1\"}"));

        CodefAccountResult result = client.unregister("cid-1", "0004", LinkedAccountType.BANK);

        Map<String, Object> body = capturedBody("/v1/account/delete");
        assertThat(body).containsEntry("connectedId", "cid-1");
        assertThat(firstAccount(body)).containsEntry("organization", "0004").containsEntry("businessType", "BK")
                .doesNotContainKeys("password", "id");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("성공 코드여도 connectedId를 얻지 못하면 실패로 본다")
    void successWithoutAnyConnectedIdIsFailure() {
        givenResponse(response("CF-00000", "성공", "{}"));

        CodefAccountResult result = client.register(null, "0004", LinkedAccountType.BANK, "codef", "pw1234", "980225");

        assertThat(result.isSuccess()).isFalse();
    }
}
