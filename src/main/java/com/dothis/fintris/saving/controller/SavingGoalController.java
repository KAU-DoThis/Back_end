package com.dothis.fintris.saving.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.saving.dto.SavingGoalCreateRequest;
import com.dothis.fintris.saving.dto.SavingGoalListResponse;
import com.dothis.fintris.saving.dto.SavingGoalResponse;
import com.dothis.fintris.saving.service.SavingGoalService;
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

@Tag(name = "SavingGoal", description = "절약 목표 (F-03). 기간이 끝난 다음 날 06:30에 확정해 절약분 나눌 돈으로 옮긴다")
@RestController
@RequestMapping("/saving-goals")
@RequiredArgsConstructor
public class SavingGoalController {

    private final SavingGoalService savingGoalService;

    @Operation(summary = "절약 목표 생성 (12)",
            description = """
                    기준(지난달 지출·결제 횟수)은 서버가 계산한다. 기간을 비우면 오늘부터 1개월.
                    - 목표는 targetCount(횟수) 또는 targetAmount(금액)로 정한다. 횟수로 보내면 목표 금액 = 지난달 지출 × 목표 횟수 ÷ 지난달 횟수
                    - 목표 ≥ 지난달: SAVING_GOAL_NO_EXPECTED_SAVING ("예상 절약액 없음" 안내)
                    - 확정은 금액 기준: 지난달 지출 - 기간 중 실제 지출
                    - 같은 카테고리에 진행 중인 목표가 있으면 SAVING_GOAL_DUPLICATE_CATEGORY
                    - linkedBlockId는 내 목표형 블록만""")
    @PostMapping
    public ApiResponse<SavingGoalResponse> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid SavingGoalCreateRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, savingGoalService.create(userId, request));
    }

    @Operation(summary = "절약 목표 목록 (05 진행 중인 목표)",
            description = "진행 중(ACTIVE)·확정(CONFIRMED)·배분 완료(ALLOCATED) 목표. 취소한 목표는 빠진다")
    @GetMapping
    public ApiResponse<SavingGoalListResponse> getGoals(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, savingGoalService.getGoals(userId));
    }

    @Operation(summary = "절약 목표 상세")
    @GetMapping("/{savingGoalId}")
    public ApiResponse<SavingGoalResponse> getGoal(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long savingGoalId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, savingGoalService.getGoal(userId, savingGoalId));
    }

    @Operation(summary = "절약 목표 취소", description = "진행 중인 목표만 취소한다")
    @DeleteMapping("/{savingGoalId}")
    public ApiResponse<SavingGoalResponse> cancel(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long savingGoalId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, savingGoalService.cancel(userId, savingGoalId));
    }
}
