package com.dothis.fintris.saving.service;

// 기간 동안 한 카테고리에 쓴 돈과 결제 횟수
public record SpentStats(long amount, long count) {

    public static final SpentStats EMPTY = new SpentStats(0, 0);
}
