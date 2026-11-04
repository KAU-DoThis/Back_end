package com.dothis.fintris.transaction.service;

import com.dothis.fintris.transaction.dto.SyncStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncStateRegistryTest {

    private final SyncStateRegistry registry = new SyncStateRegistry();

    @Test
    @DisplayName("기록이 없으면 IDLE이다")
    void defaultsToIdle() {
        assertThat(registry.get(1L).status()).isEqualTo(SyncStatus.IDLE);
    }

    @Test
    @DisplayName("진행 중이면 다시 시작할 수 없다")
    void cannotStartTwice() {
        assertThat(registry.tryStart(1L)).isTrue();
        assertThat(registry.tryStart(1L)).isFalse();
        assertThat(registry.get(1L).status()).isEqualTo(SyncStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("끝나면 다시 시작할 수 있고, 사용자마다 따로 센다")
    void canRestartAfterFinishAndIsPerUser() {
        registry.tryStart(1L);
        registry.finish(1L, new SyncSummary(2, 1));

        assertThat(registry.get(1L)).isEqualTo(new SyncStateRegistry.SyncState(SyncStatus.DONE, 1));
        assertThat(registry.tryStart(1L)).isTrue();
        assertThat(registry.tryStart(2L)).isTrue();
    }

    @Test
    @DisplayName("모든 계좌가 실패해야 FAILED이고, 계좌가 없으면 DONE이다")
    void failedOnlyWhenEveryAccountFailed() {
        registry.finish(1L, new SyncSummary(2, 2));
        registry.finish(2L, new SyncSummary(0, 0));

        assertThat(registry.get(1L).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(registry.get(2L).status()).isEqualTo(SyncStatus.DONE);
    }
}
