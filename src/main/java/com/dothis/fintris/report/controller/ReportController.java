package com.dothis.fintris.report.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.report.dto.MonthlyReportResponse;
import com.dothis.fintris.report.service.MonthlyReportService;
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

@Tag(name = "Report", description = "월간 리포트 (F-12·F-14)")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final MonthlyReportService monthlyReportService;

    @Operation(summary = "월간 리포트 (08)",
            description = """
                    month를 비우면 지난달. 이번 달을 보내면 오늘까지 집계한다(inProgress=true).
                    - 금융관리 점수 = 고정지출 확보 30 + 생활비 페이스 30 + 절약 실천 15 + 목표 달성률 25. 해당 없는 항목의 배점은 나머지에 비율대로 나눈다
                    - 그 달 이용 기간이 15일보다 짧으면 점수는 null이고 score.note에 이유
                    - 지출은 거래 목록·소비 분석과 같은 기준 (취소·소비 제외 빼고, 더치페이는 내 몫)
                    - comment는 지금은 서버 템플릿 문장""")
    @GetMapping("/monthly")
    public ApiResponse<MonthlyReportResponse> getMonthly(
            @AuthenticationPrincipal Long userId,
            @Parameter(example = "2026-10") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM")
            YearMonth month) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, monthlyReportService.getMonthly(userId, month));
    }
}
