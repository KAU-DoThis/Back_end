package com.dothis.fintris.transaction.service;

import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 동기화 뒤 거래 하나씩 블록 반영을 맞춘다. 거래 하나가 한 트랜잭션이라 블록 충돌이 나면 그 거래만 다시 시도한다
@Service
@RequiredArgsConstructor
public class BlockReflectionService {

    private final TransactionRepository transactionRepository;
    private final BlockReflector blockReflector;

    @Transactional
    public boolean reconcileIfNeeded(Long transactionId, LocalDateTime blocksConfirmedAt) {
        Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
        if (transaction == null || !blockReflector.needsReconcile(transaction, blocksConfirmedAt)) {
            return false;
        }
        return blockReflector.reconcile(transaction, blocksConfirmedAt);
    }
}
