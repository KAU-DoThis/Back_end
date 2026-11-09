package com.dothis.fintris.account.controller;

import com.dothis.fintris.account.dto.InstitutionListResponse;
import com.dothis.fintris.account.entity.LinkedAccountType;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "계좌·카드 연동 (F-19)")
@RestController
@RequestMapping("/institutions")
public class InstitutionController {

    @Operation(summary = "연동 가능 기관 목록", description = "type을 주면 은행(BANK) 또는 카드(CARD)만 보여준다")
    @GetMapping
    public ApiResponse<InstitutionListResponse> getInstitutions(
            @RequestParam(required = false) LinkedAccountType type) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, InstitutionListResponse.of(type));
    }
}
