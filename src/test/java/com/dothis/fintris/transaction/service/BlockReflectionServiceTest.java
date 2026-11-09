package com.dothis.fintris.transaction.service;

import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockReflectionServiceTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 11, 1, 12, 0);

    @Mock TransactionRepository transactionRepository;
    @Mock BlockReflector blockReflector;
    @InjectMocks BlockReflectionService service;

    @Test
    @DisplayName("다시 맞출 이유가 있는 거래만 맞춘다")
    void reconcilesOnlyWhenNeeded() {
        Transaction needs = mock(Transaction.class);
        Transaction fine = mock(Transaction.class);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(needs));
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(fine));
        when(blockReflector.needsReconcile(needs, CONFIRMED_AT)).thenReturn(true);
        when(blockReflector.needsReconcile(fine, CONFIRMED_AT)).thenReturn(false);
        when(blockReflector.reconcile(needs, CONFIRMED_AT)).thenReturn(true);

        assertThat(service.reconcileIfNeeded(1L, CONFIRMED_AT)).isTrue();
        assertThat(service.reconcileIfNeeded(2L, CONFIRMED_AT)).isFalse();
        verify(blockReflector, never()).reconcile(fine, CONFIRMED_AT);
    }

    @Test
    @DisplayName("그 사이 거래가 지워졌으면 아무것도 하지 않는다")
    void missingTransaction() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(service.reconcileIfNeeded(1L, CONFIRMED_AT)).isFalse();
        verify(blockReflector, never()).reconcile(any(), any());
    }
}
