package com.dothis.fintris.waitingfund.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.waitingfund.dto.AllocationRequest;
import com.dothis.fintris.waitingfund.dto.AllocationResponse;
import com.dothis.fintris.waitingfund.dto.WaitingFundListResponse;
import com.dothis.fintris.waitingfund.service.WaitingFundAllocationService;
import com.dothis.fintris.waitingfund.service.WaitingFundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "WaitingFund", description = "나눌 돈(대기 자금) (API 명세 v4 6-1장)")
@RestController
@RequestMapping("/waiting-funds")
@RequiredArgsConstructor
public class WaitingFundController {

    private final WaitingFundService waitingFundService;
    private final WaitingFundAllocationService waitingFundAllocationService;

    @Operation(summary = "나눌 돈 목록", description = "남은 금액이 있는 것만, 생긴 순서. 합계(total)를 같이 준다")
    @GetMapping
    public ApiResponse<WaitingFundListResponse> getWaitingFunds(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                WaitingFundListResponse.of(waitingFundService.getWaitingFunds(userId)));
    }

    @Operation(summary = "나눌 돈 배분",
            description = "미리보기(/blocks/simulate)와 같은 계산을 거친 뒤 반영한다. leftoverTo=DEFAULT_BLOCK이면 남은 돈을 생활비에 넣는다. "
                    + "나눌 돈보다 크면 WAITING_FUND_INSUFFICIENT, 블록 version이 바뀌었으면 409")
    @PostMapping("/{waitingFundId}/allocations")
    public ApiResponse<AllocationResponse> allocate(@AuthenticationPrincipal Long userId,
                                                    @PathVariable Long waitingFundId,
                                                    @RequestBody @Valid AllocationRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED,
                waitingFundAllocationService.allocate(userId, waitingFundId, request));
    }
}
