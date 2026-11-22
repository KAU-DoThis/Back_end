package com.dothis.fintris.goal.controller;

import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import com.dothis.fintris.goal.dto.GoalListResponse;
import com.dothis.fintris.goal.dto.GoalResolutionRequest;
import com.dothis.fintris.goal.dto.GoalResolutionResponse;
import com.dothis.fintris.goal.service.GoalService;
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

@Tag(name = "Goal", description = "금융 목표 (F-07). 목표 상세·생성·수정은 블록 API를 쓴다")
@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @Operation(summary = "목표 목록 조회",
            description = "목표형 블록 목록. 달성률, 지금 속도로 모으면 다 모으는 날(최근 3개월 평균 확보액 기준, "
                    + "이력이 없으면 월 확보 금액 기준), 늦어지는 일수, 상태를 함께 준다")
    @GetMapping
    public ApiResponse<GoalListResponse> getGoals(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, goalService.getGoals(userId));
    }

    @Operation(summary = "목표 달성·기한 경과 처리 (17, 17-1)",
            description = """
                    - 달성: KEEP(그대로 지키기, 매달 넣기 멈춤), RAISE_TARGET(newTargetAmount 필수), MOVE(moveTo 필수)
                    - 기한 지남: EXTEND(newDueDate 필수, 월 확보 금액 다시 계산), FINISH_AT_CURRENT(목표를 모은 금액으로), \
                    CLEANUP(moveTo로 옮기고 남은 돈은 나눌 돈으로 돌려보낸 뒤 블록 삭제)
                    - 둘 다: REMIND_LATER(지금은 바꾸지 않음)
                    - 상태에 맞지 않는 처리는 GOAL_ACTION_NOT_ALLOWED""")
    @PostMapping("/{blockId}/resolution")
    public ApiResponse<GoalResolutionResponse> resolve(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long blockId,
            @RequestBody @Valid GoalResolutionRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, goalService.resolve(userId, blockId, request));
    }
}
