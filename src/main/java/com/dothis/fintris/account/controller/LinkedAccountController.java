package com.dothis.fintris.account.controller;

import com.dothis.fintris.account.dto.LinkedAccountCreateRequest;
import com.dothis.fintris.account.dto.LinkedAccountResponse;
import com.dothis.fintris.account.service.LinkedAccountService;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Account", description = "계좌·카드 연동 (F-19)")
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class LinkedAccountController {

    private final LinkedAccountService linkedAccountService;

    @Operation(summary = "연동된 계좌 목록 조회", description = "해제된 계좌는 제외한다")
    @GetMapping
    public ApiResponse<List<LinkedAccountResponse>> getLinkedAccounts(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, linkedAccountService.getLinkedAccounts(userId));
    }

    @Operation(summary = "금융기관 연동 해제",
            description = "선택한 계좌·카드가 속한 기관의 연동을 CODEF에서 지우고, 같은 기관의 항목을 모두 해제한다")
    @DeleteMapping("/{linkedAccountId}")
    public ApiResponse<Void> unlink(@AuthenticationPrincipal Long userId, @PathVariable Long linkedAccountId) {
        linkedAccountService.unlink(userId, linkedAccountId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }

    @Operation(summary = "금융기관 연동",
            description = "기관 아이디·비밀번호로 연동하고 보유 입출금 계좌를 가져온다. 인증정보는 저장하지 않는다")
    @PostMapping
    public ApiResponse<List<LinkedAccountResponse>> link(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid LinkedAccountCreateRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, linkedAccountService.link(userId, request));
    }
}
