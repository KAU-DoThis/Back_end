package com.dothis.fintris.global.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

// 키(IP·사용자)마다 정해진 시간 동안의 요청 수를 센다. 서버 한 대 기준으로 메모리에만 둔다
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(long resetAtMs, int count) {
    }

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public RateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    // 한도 안이면 true. 시간이 지나면 처음부터 다시 센다
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.getAsLong();
        // 지난 기록이 쌓여 메모리가 계속 늘지 않게 한다
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.resetAtMs() <= now);
        }
        Window updated = windows.compute(key, (k, current) ->
                current == null || current.resetAtMs() <= now
                        ? new Window(now + window.toMillis(), 1)
                        : new Window(current.resetAtMs(), Math.min(current.count() + 1, limit + 1)));
        return updated.count() <= limit;
    }
}
