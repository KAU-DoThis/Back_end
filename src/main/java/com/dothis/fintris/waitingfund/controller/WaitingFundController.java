package com.dothis.fintris.waitingfund.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.waitingfund.dto.WaitingFundListResponse;
import com.dothis.fintris.waitingfund.service.WaitingFundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "WaitingFund", description = "나눌 돈(대기 자금) (API 명세 v4 6-1장)")
@RestController
@RequestMapping("/waiting-funds")
@RequiredArgsConstructor
public class WaitingFundController {

    private final WaitingFundService waitingFundService;

    @Operation(summary = "나눌 돈 목록", description = "남은 금액이 있는 것만, 생긴 순서. 합계(total)를 같이 준다")
    @GetMapping
    public ApiResponse<WaitingFundListResponse> getWaitingFunds(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                WaitingFundListResponse.of(waitingFundService.getWaitingFunds(userId)));
    }
}
