package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.transaction.dto.SyncStartResponse;
import com.dothis.fintris.transaction.dto.SyncStatus;
import com.dothis.fintris.transaction.dto.SyncStatusResponse;
import com.dothis.fintris.transaction.service.SyncStateRegistry.SyncState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// 수동 동기화를 요청 스레드가 기다리지 않도록 백그라운드로 돌리고, 진행 상태를 조회할 수 있게 한다
@Slf4j
@Service
public class ManualSyncService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration MIN_INTERVAL = Duration.ofHours(1);

    private final LinkedAccountRepository linkedAccountRepository;
    private final TransactionSyncService transactionSyncService;
    private final SyncStateRegistry registry;
    private final TaskExecutor executor;

    public ManualSyncService(LinkedAccountRepository linkedAccountRepository,
                             TransactionSyncService transactionSyncService,
                             SyncStateRegistry registry,
                             @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.linkedAccountRepository = linkedAccountRepository;
        this.transactionSyncService = transactionSyncService;
        this.registry = registry;
        this.executor = executor;
    }

    public SyncStartResponse start(Long userId) {
        List<LinkedAccount> targets = syncTargets(userId);
        LocalDateTime lastSyncedAt = oldestSyncedAt(targets).orElse(null);
        if (isFresh(targets)) {
            return new SyncStartResponse(SyncStatus.SKIPPED, lastSyncedAt);
        }
        // 이미 진행 중이면 새로 시작하지 않고 진행 중이라고만 알린다
        if (registry.tryStart(userId)) {
            try {
                executor.execute(() -> run(userId));
            } catch (RuntimeException e) {
                registry.fail(userId);
                throw e;
            }
        }
        return new SyncStartResponse(SyncStatus.IN_PROGRESS, lastSyncedAt);
    }

    // 정기 동기화용. 호출한 스레드에서 끝까지 돌리고, 이미 진행 중(수동 동기화 포함)이면 건너뛴다
    public void runScheduled(Long userId) {
        if (registry.tryStart(userId)) {
            run(userId);
        }
    }

    public SyncStatusResponse getStatus(Long userId) {
        SyncState state = registry.get(userId);
        return new SyncStatusResponse(state.status(),
                oldestSyncedAt(syncTargets(userId)).orElse(null), state.failedCount());
    }

    private void run(Long userId) {
        try {
            registry.finish(userId, transactionSyncService.syncUser(userId));
        } catch (RuntimeException e) {
            log.warn("수동 동기화 중 예외가 발생했습니다.");
            registry.fail(userId);
        }
    }

    private List<LinkedAccount> syncTargets(Long userId) {
        return linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(userId);
    }

    // 동기화할 계좌·카드가 없거나, 모두 1시간 안에 동기화됐으면 건너뛴다
    private boolean isFresh(List<LinkedAccount> accounts) {
        LocalDateTime threshold = LocalDateTime.now(KST).minus(MIN_INTERVAL);
        return accounts.stream().allMatch(account ->
                account.getLastSyncedAt() != null && account.getLastSyncedAt().isAfter(threshold));
    }

    // 홈·연동 목록과 같은 기준으로 가장 오래된 시각을 보여 준다
    private Optional<LocalDateTime> oldestSyncedAt(List<LinkedAccount> accounts) {
        return accounts.stream().map(LinkedAccount::getLastSyncedAt).filter(Objects::nonNull)
                .min(LocalDateTime::compareTo);
    }
}
