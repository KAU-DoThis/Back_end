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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @Operation(summary = "더치페이 조회", description = "수정 화면에서 지금 묶인 결제·받은 돈과 내 몫을 보여줄 때 쓴다")
    @GetMapping("/{dutchPayId}")
    public ApiResponse<DutchPayResponse> get(@AuthenticationPrincipal Long userId, @PathVariable Long dutchPayId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, dutchPayService.get(userId, dutchPayId));
    }

    @Operation(summary = "더치페이 수정",
            description = "고른 결제·받은 돈을 통째로 바꾼다(나중에 받은 돈 붙이기 등). 만들기와 같은 규칙으로 막고, 내 몫과 블록에 돌려놓은 돈을 다시 계산한다")
    @PatchMapping("/{dutchPayId}")
    public ApiResponse<DutchPayResponse> update(@AuthenticationPrincipal Long userId, @PathVariable Long dutchPayId,
                                                @RequestBody @Valid DutchPayRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, dutchPayService.update(userId, dutchPayId, request));
    }

    @Operation(summary = "더치페이 해제", description = "블록에 돌려놓은 돈을 다시 빼고 묶음을 지운다. 결제는 다시 전체 금액이 쓴 돈이 된다")
    @DeleteMapping("/{dutchPayId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long dutchPayId) {
        dutchPayService.delete(userId, dutchPayId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}
