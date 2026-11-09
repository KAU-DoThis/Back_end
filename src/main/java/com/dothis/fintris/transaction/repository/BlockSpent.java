package com.dothis.fintris.transaction.repository;

// 한 블록에 반영된 거래의 금액 합(spent)과 그 블록에서 실제로 뺀 금액 합(deducted). 블록이 0에서 멈추면 deducted가 작다
public record BlockSpent(Long spent, Long deducted) {
}
