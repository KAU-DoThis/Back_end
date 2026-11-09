package com.dothis.fintris.transaction.repository;

// 거래 목록 상단 요약용 합계
public record TransactionTotals(Long spent, Long income, Long provisionalCount, Long needsReviewCount) {
}
