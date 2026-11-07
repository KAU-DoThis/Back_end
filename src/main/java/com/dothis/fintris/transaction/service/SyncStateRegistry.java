package com.dothis.fintris.transaction.service;

import com.dothis.fintris.transaction.dto.SyncStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// 사용자별 동기화 진행 상태. 서버 한 대 기준으로 메모리에만 둔다 (결과 시각은 DB의 last_synced_at에 남는다)
@Component
public class SyncStateRegistry {

    public record SyncState(SyncStatus status, int failedCount) {
    }

    private static final SyncState IDLE = new SyncState(SyncStatus.IDLE, 0);
    private static final SyncState STARTED = new SyncState(SyncStatus.IN_PROGRESS, 0);

    private final ConcurrentMap<Long, SyncState> states = new ConcurrentHashMap<>();

    public SyncState get(Long userId) {
        return states.getOrDefault(userId, IDLE);
    }

    // 진행 중이 아닐 때만 시작 상태로 바꾼다. 이미 진행 중이면 false
    public boolean tryStart(Long userId) {
        boolean[] started = {false};
        states.compute(userId, (id, current) -> {
            if (current != null && current.status() == SyncStatus.IN_PROGRESS) {
                return current;
            }
            started[0] = true;
            return STARTED;
        });
        return started[0];
    }

    public void finish(Long userId, SyncSummary summary) {
        boolean allFailed = summary.total() > 0 && summary.failed() == summary.total();
        states.put(userId, new SyncState(allFailed ? SyncStatus.FAILED : SyncStatus.DONE, summary.failed()));
    }

    public void fail(Long userId) {
        states.put(userId, new SyncState(SyncStatus.FAILED, 0));
    }
}
