package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccount;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import com.dothis.fintris.transaction.dto.SyncStartResponse;
import com.dothis.fintris.transaction.dto.SyncStatus;
import com.dothis.fintris.transaction.dto.SyncStatusResponse;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualSyncServiceTest {

    private static final Long USER_ID = 1L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock LinkedAccountRepository linkedAccountRepository;
    @Mock TransactionSyncService transactionSyncService;

    private final SyncStateRegistry registry = new SyncStateRegistry();
    private final List<Runnable> queued = new ArrayList<>();
    private final TaskExecutor queueingExecutor = queued::add;
    private ManualSyncService service;

    private final User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();

    @BeforeEach
    void setUp() {
        service = new ManualSyncService(linkedAccountRepository, transactionSyncService, registry, queueingExecutor);
    }

    private LinkedAccount bank(LocalDateTime lastSyncedAt) {
        return LinkedAccount.builder().user(user).institutionCode("0004").type(LinkedAccountType.BANK)
                .connectedId("cid").lastSyncedAt(lastSyncedAt).build();
    }

    private void givenAccounts(LinkedAccount... accounts) {
        when(linkedAccountRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(accounts));
    }

    @Test
    @DisplayName("모든 계좌가 1시간 안에 동기화됐으면 건너뛴다")
    void skipsWhenEveryAccountIsFresh() {
        LocalDateTime recent = LocalDateTime.now(KST).minusMinutes(10);
        givenAccounts(bank(recent));

        SyncStartResponse response = service.start(USER_ID);

        assertThat(response.syncStatus()).isEqualTo(SyncStatus.SKIPPED);
        assertThat(response.lastSyncedAt()).isEqualTo(recent);
        assertThat(queued).isEmpty();
    }

    @Test
    @DisplayName("동기화할 은행 계좌가 없으면 건너뛴다")
    void skipsWhenNoBankAccount() {
        givenAccounts();

        assertThat(service.start(USER_ID).syncStatus()).isEqualTo(SyncStatus.SKIPPED);
        assertThat(queued).isEmpty();
    }

    @Test
    @DisplayName("오래됐거나 한 번도 안 한 계좌가 있으면 백그라운드로 시작하고, 끝나면 DONE이다")
    void startsInBackgroundAndFinishes() {
        givenAccounts(bank(LocalDateTime.now(KST).minusHours(2)));
        when(transactionSyncService.syncUser(USER_ID)).thenReturn(new SyncSummary(1, 0));

        SyncStartResponse response = service.start(USER_ID);

        assertThat(response.syncStatus()).isEqualTo(SyncStatus.IN_PROGRESS);
        assertThat(service.getStatus(USER_ID).syncStatus()).isEqualTo(SyncStatus.IN_PROGRESS);
        verify(transactionSyncService, never()).syncUser(USER_ID);

        queued.get(0).run();

        SyncStatusResponse status = service.getStatus(USER_ID);
        assertThat(status.syncStatus()).isEqualTo(SyncStatus.DONE);
        assertThat(status.failedCount()).isZero();
    }

    @Test
    @DisplayName("이미 진행 중이면 새로 시작하지 않고 진행 중이라고만 알린다")
    void doesNotStartTwiceWhileRunning() {
        givenAccounts(bank(null));

        service.start(USER_ID);
        SyncStartResponse second = service.start(USER_ID);

        assertThat(second.syncStatus()).isEqualTo(SyncStatus.IN_PROGRESS);
        assertThat(queued).hasSize(1);
    }

    @Test
    @DisplayName("계좌가 모두 실패하면 FAILED이고 실패 수를 알려준다")
    void reportsFailedWhenEveryAccountFails() {
        givenAccounts(bank(null));
        when(transactionSyncService.syncUser(USER_ID)).thenReturn(new SyncSummary(1, 1));

        service.start(USER_ID);
        queued.get(0).run();

        SyncStatusResponse status = service.getStatus(USER_ID);
        assertThat(status.syncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(status.failedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("백그라운드 실행 중 예외가 나도 FAILED로 끝나 다시 시작할 수 있다")
    void unexpectedExceptionEndsAsFailed() {
        givenAccounts(bank(null));
        when(transactionSyncService.syncUser(USER_ID)).thenThrow(new IllegalStateException("db down"));

        service.start(USER_ID);
        queued.get(0).run();

        assertThat(service.getStatus(USER_ID).syncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(registry.tryStart(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("실행기가 작업을 거부하면 진행 중 상태를 풀고 예외를 그대로 던진다")
    void rejectedExecutionResetsState() {
        givenAccounts(bank(null));
        RuntimeException rejected = new java.util.concurrent.RejectedExecutionException("full");
        service = new ManualSyncService(linkedAccountRepository, transactionSyncService, registry,
                task -> { throw rejected; });

        assertThatThrownBy(() -> service.start(USER_ID)).isSameAs(rejected);
        assertThat(registry.get(USER_ID).status()).isEqualTo(SyncStatus.FAILED);
    }

    @Test
    @DisplayName("상태 조회는 기록이 없으면 IDLE과 마지막 동기화 시각을 준다")
    void statusIsIdleWithLastSyncedAtByDefault() {
        LocalDateTime synced = LocalDateTime.of(2026, 10, 14, 9, 0);
        givenAccounts(bank(synced));

        SyncStatusResponse status = service.getStatus(USER_ID);

        assertThat(status.syncStatus()).isEqualTo(SyncStatus.IDLE);
        assertThat(status.lastSyncedAt()).isEqualTo(synced);
        assertThat(status.failedCount()).isZero();
    }
}
