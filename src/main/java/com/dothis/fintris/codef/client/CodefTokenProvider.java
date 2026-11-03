package com.dothis.fintris.codef.client;

import com.dothis.fintris.codef.client.CodefTokenClient.CodefToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

// 토큰은 약 1주일 유효하므로 호출마다 발급하지 않고 메모리에 두고 재사용한다
@Component
@RequiredArgsConstructor
public class CodefTokenProvider {

    // 만료 직전에 요청이 실패하지 않도록 여유를 둔다
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(10);

    private final CodefTokenClient codefTokenClient;

    private String accessToken;
    private Instant expiresAt = Instant.MIN;

    public synchronized String getAccessToken() {
        if (accessToken == null || !Instant.now().isBefore(expiresAt)) {
            CodefToken token = codefTokenClient.issue();
            accessToken = token.accessToken();
            expiresAt = Instant.now().plusSeconds(token.expiresInSeconds()).minus(EXPIRY_MARGIN);
        }
        return accessToken;
    }

    // CODEF가 토큰 만료(CF-99997)를 알려오면 캐시를 비워 다음 호출에서 재발급한다
    public synchronized void invalidate() {
        accessToken = null;
        expiresAt = Instant.MIN;
    }
}
