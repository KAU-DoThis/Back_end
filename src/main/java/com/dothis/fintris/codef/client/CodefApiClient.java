package com.dothis.fintris.codef.client;

import com.dothis.fintris.codef.code.CodefErrorCode;
import com.dothis.fintris.global.config.properties.CodefProperties;
import com.dothis.fintris.global.exception.GeneralException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class CodefApiClient {

    private static final String SUCCESS_CODE = "CF-00000";
    private static final String TOKEN_EXPIRED_CODE = "CF-99997";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    // 기능명세서: CODEF 응답이 30초를 넘으면 백그라운드 재시도 대상으로 본다
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final CodefProperties codefProperties;
    private final CodefTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public CodefApiClient(CodefProperties codefProperties, CodefTokenProvider tokenProvider,
                          ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.codefProperties = codefProperties;
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.clone()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT)))
                .build();
    }

    // CODEF는 성공·실패와 관계없이 HTTP 200으로 응답하므로 결과는 result.code로 판단한다
    public CodefResponse post(String path, Object body) {
        CodefResponse response;
        try {
            response = exchange(path, body);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 401) {
                throw GeneralException.of(CodefErrorCode.CODEF_API_FAILED, e);
            }
            // 토큰 만료·무효이면 캐시를 비우고 한 번만 재시도한다
            return retryWithNewToken(path, body);
        } catch (RestClientException e) {
            throw translate(e);
        }
        // 확인된 건 HTTP 401이지만, 토큰 만료가 200 본문으로 올 때도 같은 방식으로 한 번만 재시도한다
        if (response.result() != null && TOKEN_EXPIRED_CODE.equals(response.result().code())) {
            return retryWithNewToken(path, body);
        }
        return response;
    }

    private CodefResponse retryWithNewToken(String path, Object body) {
        tokenProvider.invalidate();
        try {
            return exchange(path, body);
        } catch (RestClientException e) {
            throw translate(e);
        }
    }

    private CodefResponse exchange(String path, Object body) {
        String raw = restClient.post()
                .uri(codefProperties.getBaseUrl() + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        return parse(raw);
    }

    // 응답 본문은 URL 인코딩된 JSON 문자열로 온다
    private CodefResponse parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw GeneralException.of(CodefErrorCode.CODEF_API_FAILED);
        }
        CodefResponse response;
        try {
            String json = raw.startsWith("{") ? raw : URLDecoder.decode(raw, StandardCharsets.UTF_8);
            response = objectMapper.readValue(json, CodefResponse.class);
        } catch (IllegalArgumentException | JacksonException e) {
            throw GeneralException.of(CodefErrorCode.CODEF_API_FAILED, e);
        }
        if (response.result() == null) {
            throw GeneralException.of(CodefErrorCode.CODEF_API_FAILED);
        }
        return response;
    }

    private GeneralException translate(RestClientException e) {
        if (e instanceof ResourceAccessException && isTimeout(e)) {
            return GeneralException.of(CodefErrorCode.CODEF_API_TIMEOUT, e);
        }
        return GeneralException.of(CodefErrorCode.CODEF_API_FAILED, e);
    }

    private boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodefResponse(CodefResult result, JsonNode data) {

        public boolean isSuccess() {
            return result != null && SUCCESS_CODE.equals(result.code());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodefResult(String code, String message, String extraMessage, String transactionId) {
    }
}
