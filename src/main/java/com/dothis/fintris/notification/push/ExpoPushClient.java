package com.dothis.fintris.notification.push;

import com.dothis.fintris.global.config.properties.ExpoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Expo 푸시 발송. 한 번에 100건까지 보낼 수 있다
// 응답 data는 보낸 순서대로 {status: ok|error, details.error}가 온다. DeviceNotRegistered면 토큰을 지워야 한다
@Slf4j
@Component
public class ExpoPushClient {

    static final int MAX_BATCH = 100;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ExpoProperties expoProperties;
    private final RestClient restClient;

    public ExpoPushClient(ExpoProperties expoProperties, RestClient.Builder restClientBuilder) {
        this.expoProperties = expoProperties;
        this.restClient = restClientBuilder.clone()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults().withTimeouts(TIMEOUT, TIMEOUT)))
                .build();
    }

    // 결과는 messages와 같은 순서. 호출이 실패하면 모두 FAILED로 돌려주고 예외를 던지지 않는다
    public List<PushResult> send(List<PushMessage> messages) {
        List<PushResult> results = new ArrayList<>(messages.size());
        for (int from = 0; from < messages.size(); from += MAX_BATCH) {
            List<PushMessage> batch = messages.subList(from, Math.min(from + MAX_BATCH, messages.size()));
            results.addAll(sendBatch(batch));
        }
        return results;
    }

    private List<PushResult> sendBatch(List<PushMessage> batch) {
        List<Map<String, Object>> body = batch.stream().map(PushMessage::toBody).toList();
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(expoProperties.getPushUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            String token = expoProperties.getAccessToken();
            if (token != null && !token.isBlank()) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            JsonNode response = request.body(body).retrieve().body(JsonNode.class);
            return parse(response, batch.size());
        } catch (RestClientException e) {
            log.warn("Expo 푸시 발송에 실패했습니다. 건수={}", batch.size());
            return batch.stream().map(message -> PushResult.FAILED).toList();
        }
    }

    static List<PushResult> parse(JsonNode response, int size) {
        JsonNode data = response == null ? null : response.path("data");
        List<PushResult> results = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            JsonNode ticket = data == null || !data.isArray() ? null : data.get(i);
            results.add(PushResult.of(ticket));
        }
        return results;
    }

    public enum PushResult {
        SENT,
        UNREGISTERED, // 앱을 지웠거나 토큰이 만료됨. 토큰을 지운다
        FAILED;

        static PushResult of(JsonNode ticket) {
            if (ticket == null) {
                return FAILED;
            }
            if ("ok".equals(ticket.path("status").asString(""))) {
                return SENT;
            }
            return "DeviceNotRegistered".equals(ticket.path("details").path("error").asString("")) ? UNREGISTERED : FAILED;
        }
    }
}
