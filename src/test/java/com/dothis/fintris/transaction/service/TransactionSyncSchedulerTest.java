package com.dothis.fintris.transaction.service;

import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.account.repository.LinkedAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionSyncSchedulerTest {

    @Mock LinkedAccountRepository linkedAccountRepository;
    @Mock ManualSyncService manualSyncService;
    @InjectMocks TransactionSyncScheduler scheduler;

    @Test
    @DisplayName("은행 계좌가 있는 모든 사용자를 동기화한다")
    void syncsEveryUserWithBankAccount() {
        when(linkedAccountRepository.findUserIdsWithActive(LinkedAccountType.BANK)).thenReturn(List.of(1L, 2L));

        scheduler.syncAllUsers();

        verify(manualSyncService).runScheduled(1L);
        verify(manualSyncService).runScheduled(2L);
    }

    @Test
    @DisplayName("한 사용자에서 예외가 나도 다음 사용자를 계속 동기화한다")
    void continuesAfterUserFailure() {
        when(linkedAccountRepository.findUserIdsWithActive(LinkedAccountType.BANK)).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("boom")).when(manualSyncService).runScheduled(1L);

        scheduler.syncAllUsers();

        verify(manualSyncService).runScheduled(2L);
    }
}
