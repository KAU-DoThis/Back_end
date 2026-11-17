package com.dothis.fintris.global.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

// 키(IP·사용자)마다 정해진 시간 동안의 요청 수를 센다. 서버 한 대 기준으로 메모리에만 둔다
public class RateLimiter {

    static final int CLEANUP_THRESHOLD = 10_000;
    private static final long CLEANUP_INTERVAL_MS = Duration.ofMinutes(1).toMillis();

    private record Window(long resetAtMs, int count) {
    }

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupAtMs = new AtomicLong();
    private final LongSupplier clock;

    public RateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    // 한도 안이면 true. 시간이 지나면 처음부터 다시 센다
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.getAsLong();
        // 지난 기록이 쌓여 메모리가 계속 늘지 않게 한다. 요청마다 다 훑지 않게 1분에 한 번, 한 스레드만 정리한다
        long nextCleanup = nextCleanupAtMs.get();
        if (windows.size() > CLEANUP_THRESHOLD && now >= nextCleanup
                && nextCleanupAtMs.compareAndSet(nextCleanup, now + CLEANUP_INTERVAL_MS)) {
            windows.values().removeIf(w -> w.resetAtMs() <= now);
        }
        Window updated = windows.compute(key, (k, current) ->
                current == null || current.resetAtMs() <= now
                        ? new Window(now + window.toMillis(), 1)
                        : new Window(current.resetAtMs(), Math.min(current.count() + 1, limit + 1)));
        return updated.count() <= limit;
    }
}
