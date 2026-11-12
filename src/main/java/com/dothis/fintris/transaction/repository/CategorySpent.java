package com.dothis.fintris.transaction.repository;

// 분류 전 거래는 categoryId·code·name이 null이다
public record CategorySpent(Long categoryId, String code, String name, Long count, Long amount) {
}
