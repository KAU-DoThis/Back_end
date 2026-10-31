package com.dothis.fintris.transaction.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.transaction.dto.AccountSyncResponse;
import com.dothis.fintris.transaction.service.TransactionSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Transaction", description = "거래내역 동기화 (F-19)")
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionSyncController {

    private final TransactionSyncService transactionSyncService;

    @Operation(summary = "거래내역 동기화",
            description = "연동된 입출금 계좌의 거래내역을 CODEF에서 가져온다. 처음이면 최근 3개월, 이후에는 변경분만 가져온다")
    @PostMapping("/sync")
    public ApiResponse<List<AccountSyncResponse>> sync(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, transactionSyncService.syncUser(userId));
    }
}
