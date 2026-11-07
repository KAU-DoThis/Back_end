package com.dothis.fintris.transaction.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.transaction.dto.TransactionResponse;
import com.dothis.fintris.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transaction", description = "거래 조회 (F-19·F-21)")
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "거래 상세 조회", description = "다른 사용자의 거래면 403")
    @GetMapping("/{transactionId}")
    public ApiResponse<TransactionResponse> getTransaction(@AuthenticationPrincipal Long userId,
                                                           @PathVariable Long transactionId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, transactionService.getTransaction(userId, transactionId));
    }
}
