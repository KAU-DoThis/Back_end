package com.dothis.fintris.transaction.service;

import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.code.TransactionErrorCode;
import com.dothis.fintris.transaction.dto.TransactionResponse;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionResponse getTransaction(Long userId, Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> GeneralException.of(TransactionErrorCode.TRANSACTION_NOT_FOUND));
        if (!transaction.isOwnedBy(userId)) {
            throw GeneralException.of(TransactionErrorCode.TRANSACTION_FORBIDDEN);
        }
        return TransactionResponse.from(transaction);
    }
}
