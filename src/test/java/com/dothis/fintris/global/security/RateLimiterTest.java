package com.dothis.fintris.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final AtomicLong now = new AtomicLong(0);
    private final RateLimiter rateLimiter = new RateLimiter(now::get);

    @Test
    @DisplayName("한도까지는 받고 그 다음부터 막는다")
    void blocksOverLimit() {
        assertThat(rateLimiter.tryAcquire("a", 2, MINUTE)).isTrue();
        assertThat(rateLimiter.tryAcquire("a", 2, MINUTE)).isTrue();
        assertThat(rateLimiter.tryAcquire("a", 2, MINUTE)).isFalse();
    }

    @Test
    @DisplayName("키가 다르면 따로 센다")
    void countsPerKey() {
        assertThat(rateLimiter.tryAcquire("a", 1, MINUTE)).isTrue();
        assertThat(rateLimiter.tryAcquire("b", 1, MINUTE)).isTrue();
    }

    @Test
    @DisplayName("시간이 지나면 다시 받는다")
    void resetsAfterWindow() {
        assertThat(rateLimiter.tryAcquire("a", 1, MINUTE)).isTrue();
        assertThat(rateLimiter.tryAcquire("a", 1, MINUTE)).isFalse();

        now.set(MINUTE.toMillis() - 1);
        assertThat(rateLimiter.tryAcquire("a", 1, MINUTE)).isFalse();

        now.set(MINUTE.toMillis());
        assertThat(rateLimiter.tryAcquire("a", 1, MINUTE)).isTrue();
    }

    @Test
    @DisplayName("기록이 많아져 정리해도 아직 시간이 안 지난 키는 계속 막는다")
    void keepsActiveKeyAfterCleanup() {
        Duration twoMinutes = Duration.ofMinutes(2);
        assertThat(rateLimiter.tryAcquire("active", 1, twoMinutes)).isTrue();
        for (int i = 0; i < RateLimiter.CLEANUP_THRESHOLD; i++) {
            rateLimiter.tryAcquire("old" + i, 1, MINUTE);
        }

        // old 키들은 만료됐고 active는 아직이다. 이 요청에서 정리가 돈다
        now.set(MINUTE.toMillis());
        rateLimiter.tryAcquire("trigger", 1, MINUTE);

        assertThat(rateLimiter.tryAcquire("active", 1, twoMinutes)).isFalse();
    }
}
