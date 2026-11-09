package com.dothis.fintris.transaction.service;

// 한 번의 동기화에서 대상 계좌 수와 실패한 계좌 수
public record SyncSummary(int total, int failed) {
}
