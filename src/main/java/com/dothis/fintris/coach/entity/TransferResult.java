package com.dothis.fintris.coach.entity;

// 옮긴(또는 되돌린) 뒤 잔액. 블록 잔액은 계속 바뀌므로 그 시점 값을 저장해 결과 카드를 다시 그린다
public record TransferResult(long fromBalanceAfter, long toBalanceAfter) {
}
