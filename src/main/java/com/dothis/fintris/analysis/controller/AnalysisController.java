package com.dothis.fintris.analysis.controller;

import com.dothis.fintris.analysis.dto.SpendingAnalysisResponse;
import com.dothis.fintris.analysis.service.SpendingAnalysisService;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@Tag(name = "Analysis", description = "소비 분석 (F-01)")
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final SpendingAnalysisService spendingAnalysisService;

    @Operation(summary = "소비 분석 조회",
            description = "API 명세 v4 8장. month를 빼면 이번 달. 이번 달은 지난달 같은 기간과, 지난 달은 그 전달 전체와 비교한다. "
                    + "biggestIncrease의 explanation·excludedNote는 아직 null, savingPotential은 아직 응답에 없다")
    @GetMapping("/spending")
    public ApiResponse<SpendingAnalysisResponse> getSpending(
            @AuthenticationPrincipal Long userId,
            @Parameter(example = "2026-10") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, spendingAnalysisService.getSpending(userId, month));
    }
}
