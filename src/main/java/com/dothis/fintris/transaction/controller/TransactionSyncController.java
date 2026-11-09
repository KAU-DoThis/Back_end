package com.dothis.fintris.transaction.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.transaction.dto.SyncStartResponse;
import com.dothis.fintris.transaction.dto.SyncStatusResponse;
import com.dothis.fintris.transaction.service.ManualSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transaction", description = "거래내역 동기화 (F-19)")
@RestController
@RequestMapping("/linked-accounts")
@RequiredArgsConstructor
public class TransactionSyncController {

    private final ManualSyncService manualSyncService;

    @Operation(summary = "수동 동기화",
            description = "연동된 입출금 계좌의 거래내역을 백그라운드에서 가져온다. 마지막 동기화가 1시간 이내면 SKIPPED")
    @PostMapping("/sync")
    public ApiResponse<SyncStartResponse> sync(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.ACCEPTED, manualSyncService.start(userId));
    }

    @Operation(summary = "동기화 상태 조회", description = "진행 중인지, 마지막 결과와 실패한 계좌 수를 알려준다")
    @GetMapping("/sync-status")
    public ApiResponse<SyncStatusResponse> getSyncStatus(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, manualSyncService.getStatus(userId));
    }
}
