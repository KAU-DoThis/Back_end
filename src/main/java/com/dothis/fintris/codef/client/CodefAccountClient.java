package com.dothis.fintris.codef.client;

import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.codef.client.CodefApiClient.CodefResponse;
import com.dothis.fintris.global.crypto.RsaEncryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CodefAccountClient {

    private static final String CREATE_PATH = "/v1/account/create";
    private static final String ADD_PATH = "/v1/account/add";
    private static final String DELETE_PATH = "/v1/account/delete";

    private final CodefApiClient codefApiClient;
    private final RsaEncryptor rsaEncryptor;

    // connectedId가 없으면 새로 발급(create)하고, 있으면 그 connectedId에 기관을 추가(add)한다
    public CodefAccountResult register(String connectedId, String organization, LinkedAccountType type,
                                       String loginId, String password, String birthDate) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("countryCode", "KR");
        account.put("businessType", type == LinkedAccountType.BANK ? "BK" : "CD");
        account.put("clientType", "P");
        account.put("organization", organization);
        account.put("loginType", "1"); // 아이디·비밀번호 로그인만 지원한다
        account.put("id", loginId);
        account.put("password", rsaEncryptor.encrypt(password));
        account.put("birthDate", birthDate);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountList", List.of(account));
        if (connectedId != null) {
            body.put("connectedId", connectedId);
        }

        CodefResponse response = codefApiClient.post(connectedId == null ? CREATE_PATH : ADD_PATH, body);
        return toResult(response, connectedId);
    }

    // 기관 하나의 계정 등록을 CODEF에서 지운다. connectedId는 그대로 남는다
    public CodefAccountResult unregister(String connectedId, String organization, LinkedAccountType type) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("countryCode", "KR");
        account.put("businessType", type == LinkedAccountType.BANK ? "BK" : "CD");
        account.put("clientType", "P");
        account.put("organization", organization);
        account.put("loginType", "1");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountList", List.of(account));
        body.put("connectedId", connectedId);

        return toResult(codefApiClient.post(DELETE_PATH, body), connectedId);
    }

    // 등록이 실패해도 HTTP 200으로 오므로 errorList의 코드·메시지를 결과로 돌려준다
    private CodefAccountResult toResult(CodefResponse response, String requestedConnectedId) {
        JsonNode data = response.data();
        String issuedConnectedId = text(data, "connectedId");
        if (response.isSuccess() && (issuedConnectedId != null || requestedConnectedId != null)) {
            return CodefAccountResult.success(issuedConnectedId != null ? issuedConnectedId : requestedConnectedId);
        }
        JsonNode firstError = data == null ? null : data.path("errorList").path(0);
        String code = text(firstError, "code");
        String message = text(firstError, "message");
        return CodefAccountResult.failure(
                code != null ? code : response.result().code(),
                message != null ? message : response.result().message());
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    public record CodefAccountResult(String connectedId, String failureCode, String failureMessage) {

        static CodefAccountResult success(String connectedId) {
            return new CodefAccountResult(connectedId, null, null);
        }

        static CodefAccountResult failure(String code, String message) {
            return new CodefAccountResult(null, code, message);
        }

        public boolean isSuccess() {
            return connectedId != null;
        }
    }
}
