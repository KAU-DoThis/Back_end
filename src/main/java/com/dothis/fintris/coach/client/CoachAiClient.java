package com.dothis.fintris.coach.client;

import com.dothis.fintris.global.config.properties.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;

// 초불이 대화 (AI 서버 내부 API, FE 비공개). 실패하면 비워서 돌려주고 호출한 쪽이 고정 문장으로 답한다
@Slf4j
@Component
public class CoachAiClient {

    // TBD(AI 담당과 확정): 경로와 요청·응답 형식
    static final String CHAT_PATH = "/internal/ai/coach/chat";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(40);

    private final AiProperties aiProperties;
    private final RestClient restClient;

    public CoachAiClient(AiProperties aiProperties, RestClient.Builder restClientBuilder) {
        this.aiProperties = aiProperties;
        this.restClient = restClientBuilder.clone()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT)))
                .build();
    }

    public Optional<CoachAiResponse> chat(CoachAiRequest request) {
        try {
            CoachAiResponse response = restClient.post()
                    .uri(aiProperties.getBaseUrl() + CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(CoachAiResponse.class);
            if (response == null || response.text() == null || response.text().isBlank()) {
                log.warn("초불이 AI 응답이 비었습니다. userId={}", request.userId());
                return Optional.empty();
            }
            return Optional.of(response);
        } catch (RestClientException e) {
            log.warn("초불이 AI 호출 실패. userId={}, cause={}", request.userId(), e.getMessage());
            return Optional.empty();
        }
    }
}
