package com.dothis.fintris.codef.client;

import com.dothis.fintris.codef.code.CodefErrorCode;
import com.dothis.fintris.global.config.properties.CodefProperties;
import com.dothis.fintris.global.exception.GeneralException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
public class CodefTokenClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final CodefProperties codefProperties;
    private final RestClient restClient;

    public CodefTokenClient(CodefProperties codefProperties, RestClient.Builder restClientBuilder) {
        this.codefProperties = codefProperties;
        this.restClient = restClientBuilder.clone()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT)))
                .build();
    }

    // clientId·clientSecret으로 액세스 토큰을 새로 발급받는다
    public CodefToken issue() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "read");

        try {
            CodefTokenResponse response = restClient.post()
                    .uri(codefProperties.getOauthUrl())
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(CodefTokenResponse.class);

            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw GeneralException.of(CodefErrorCode.CODEF_TOKEN_ISSUE_FAILED);
            }
            return new CodefToken(response.accessToken(), response.expiresIn());
        } catch (RestClientException e) {
            throw GeneralException.of(CodefErrorCode.CODEF_TOKEN_ISSUE_FAILED, e);
        }
    }

    private String basicAuth() {
        String credentials = codefProperties.getClientId() + ":" + codefProperties.getClientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public record CodefToken(String accessToken, long expiresInSeconds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CodefTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {
    }
}
