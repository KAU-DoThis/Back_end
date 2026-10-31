package com.dothis.fintris.auth.client;

import com.dothis.fintris.auth.code.AuthErrorCode;
import com.dothis.fintris.global.config.properties.KakaoProperties;
import com.dothis.fintris.global.exception.GeneralException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

@Component
public class KakaoApiClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final KakaoProperties kakaoProperties;
    private final RestClient restClient;

    public KakaoApiClient(KakaoProperties kakaoProperties, RestClient.Builder restClientBuilder) {
        this.kakaoProperties = kakaoProperties;
        this.restClient = restClientBuilder.clone()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT)))
                .build();
    }

    // 앱이 카카오 SDK로 받은 액세스 토큰으로 사용자 정보를 조회한다
    public KakaoUserInfo getUserInfo(String accessToken) {
        try {
            KakaoUserResponse response = restClient.get()
                    .uri(kakaoProperties.getUserInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(KakaoUserResponse.class);

            if (response == null || response.id() == null) {
                throw GeneralException.of(AuthErrorCode.AUTH_KAKAO_UNAUTHORIZED);
            }
            // 이메일 제공 동의를 안 했으면 kakao_account나 email이 없을 수 있다
            String email = response.kakaoAccount() == null ? null : response.kakaoAccount().email();
            return new KakaoUserInfo(String.valueOf(response.id()), email);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw GeneralException.of(AuthErrorCode.AUTH_KAKAO_UNAUTHORIZED, e);
            }
            throw GeneralException.of(AuthErrorCode.AUTH_KAKAO_API_FAILED, e);
        } catch (RestClientException e) {
            throw GeneralException.of(AuthErrorCode.AUTH_KAKAO_API_FAILED, e);
        }
    }

    public record KakaoUserInfo(String providerUserId, String email) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoAccount(String email) {
    }
}
