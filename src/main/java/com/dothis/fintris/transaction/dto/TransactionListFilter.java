package com.dothis.fintris.transaction.dto;

import com.dothis.fintris.transaction.entity.TransactionStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransactionListFilter {
    ALL(null),
    NEEDS_REVIEW(TransactionStatus.NEEDS_REVIEW), // 확인 필요
    PROVISIONAL(TransactionStatus.PROVISIONAL);   // 임시

    private final TransactionStatus status; // ALL은 null
}
