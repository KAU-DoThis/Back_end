package com.dothis.fintris.dutchpay.controller;

import com.dothis.fintris.dutchpay.dto.DutchPayRequest;
import com.dothis.fintris.dutchpay.dto.DutchPayResponse;
import com.dothis.fintris.dutchpay.service.DutchPayService;
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

@Tag(name = "DutchPay", description = "더치페이 (N-06). 거래 목록 [1/N] 버튼")
@RestController
@RequestMapping("/dutch-pays")
@RequiredArgsConstructor
public class DutchPayController {

    private final DutchPayService dutchPayService;

    @Operation(summary = "더치페이 만들기",
            description = "고른 결제(출금)와 받은 돈(입금)을 묶는다. 받은 돈을 결제 금액 비율대로 빼서 결제마다 내 몫을 남기고, "
                    + "결제마다 (결제 금액 - 내 몫)을 그 결제가 반영된 블록에 돌려놓는다. 이미 다른 더치페이에 들어간 거래가 있으면 409")
    @PostMapping
    public ApiResponse<DutchPayResponse> create(@AuthenticationPrincipal Long userId,
                                                @RequestBody @Valid DutchPayRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, dutchPayService.create(userId, request));
    }
}
