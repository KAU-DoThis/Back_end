package com.dothis.fintris.block.controller;

import com.dothis.fintris.block.dto.TransferRequest;
import com.dothis.fintris.block.dto.TransferResponse;
import com.dothis.fintris.block.service.BlockTransferService;
import com.dothis.fintris.block.simulation.BlockSimulationService;
import com.dothis.fintris.block.simulation.SimulateRequest;
import com.dothis.fintris.block.simulation.SimulateResponse;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BlockTransfer", description = "자금 이동·배분 (앱 시안 03-2, 06 / API 명세 v4 6장)")
@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class BlockTransferController {

    private final BlockSimulationService blockSimulationService;
    private final BlockTransferService blockTransferService;

    @Operation(summary = "이동·배분 미리보기",
            description = "블록 → 블록 또는 나눌 돈 → 블록. 저장하지 않고 결과 항목만 계산한다. "
                    + "잔액보다 큰 금액이면 blocked, 규칙이 깨지면 requiresConfirm")
    @PostMapping("/simulate")
    public ApiResponse<SimulateResponse> simulate(@AuthenticationPrincipal Long userId,
                                                  @RequestBody @Valid SimulateRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                blockSimulationService.simulate(userId, request.changes()).response());
    }

    @Operation(summary = "블록 간 이동 확정",
            description = "미리보기와 같은 계산을 거친 뒤 반영한다. version이 바뀌었으면 409 BLOCK_VERSION_CONFLICT, "
                    + "잔액보다 크면 TRANSFER_INSUFFICIENT_BALANCE, requiresConfirm인데 confirmed가 없으면 TRANSFER_CONFIRM_REQUIRED")
    @PostMapping("/transfers")
    public ApiResponse<TransferResponse> transfer(@AuthenticationPrincipal Long userId,
                                                  @RequestBody @Valid TransferRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, blockTransferService.transfer(userId, request));
    }
}
