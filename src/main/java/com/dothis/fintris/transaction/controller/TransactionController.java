package com.dothis.fintris.transaction.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.transaction.dto.TransactionListFilter;
import com.dothis.fintris.transaction.dto.TransactionListResponse;
import com.dothis.fintris.transaction.dto.TransactionResponse;
import com.dothis.fintris.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@Tag(name = "Transaction", description = "거래 조회 (F-19·F-21)")
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionService transactionService;

    @Operation(summary = "거래 목록 조회",
            description = "월 단위로 최신순 날짜별 묶음을 돌려준다. month를 빼면 이번 달, size는 최대 100")
    @GetMapping
    public ApiResponse<TransactionListResponse> getTransactions(
            @AuthenticationPrincipal Long userId,
            @Parameter(example = "2026-10") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Long linkedAccountId,
            @RequestParam(required = false) Long blockId,
            @RequestParam(defaultValue = "ALL") TransactionListFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, transactionService.getTransactions(
                userId, month, linkedAccountId, blockId, filter, Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
    }

    @Operation(summary = "거래 상세 조회", description = "다른 사용자의 거래면 403")
    @GetMapping("/{transactionId}")
    public ApiResponse<TransactionResponse> getTransaction(@AuthenticationPrincipal Long userId,
                                                           @PathVariable Long transactionId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, transactionService.getTransaction(userId, transactionId));
    }
}
