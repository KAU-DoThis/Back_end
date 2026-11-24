package com.dothis.fintris.contextquestion.controller;

import com.dothis.fintris.contextquestion.dto.ContextRuleCreateRequest;
import com.dothis.fintris.contextquestion.dto.ContextRuleListResponse;
import com.dothis.fintris.contextquestion.dto.ContextRuleResponse;
import com.dothis.fintris.contextquestion.service.ContextRuleService;
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

@Tag(name = "ContextRule", description = "맥락 규칙 — 다음부터 묻지 않고 자동 적용 (API 명세 v4 9장)")
@RestController
@RequestMapping("/context-rules")
@RequiredArgsConstructor
public class ContextRuleController {

    private final ContextRuleService contextRuleService;

    @Operation(summary = "규칙 저장",
            description = "답한 질문으로 만든다. 답변 응답의 suggestRule을 수락했을 때 부른다. 같은 규칙이 있으면 409")
    @PostMapping
    public ApiResponse<ContextRuleResponse> create(@AuthenticationPrincipal Long userId,
                                                   @RequestBody @Valid ContextRuleCreateRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, contextRuleService.create(userId, request.questionId()));
    }

    @Operation(summary = "규칙 목록", description = "15 마이 화면. 최근에 만든 순")
    @GetMapping
    public ApiResponse<ContextRuleListResponse> getRules(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, new ContextRuleListResponse(contextRuleService.getRules(userId)));
    }

    @Operation(summary = "규칙 삭제", description = "지우면 다음부터 같은 거래를 다시 묻는다")
    @DeleteMapping("/{ruleId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long ruleId) {
        contextRuleService.delete(userId, ruleId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}
