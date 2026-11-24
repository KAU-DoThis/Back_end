package com.dothis.fintris.contextquestion.controller;

import com.dothis.fintris.contextquestion.dto.ContextQuestionListResponse;
import com.dothis.fintris.contextquestion.entity.QuestionStatus;
import com.dothis.fintris.contextquestion.service.ContextQuestionService;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ContextQuestion", description = "맥락 질문 (API 명세 v4 9장)")
@RestController
@RequestMapping("/context-questions")
@RequiredArgsConstructor
public class ContextQuestionController {

    private final ContextQuestionService contextQuestionService;

    @Operation(summary = "맥락 질문 목록",
            description = "최근 거래 순. status=PENDING이면 아직 답하지 않은 질문을 모두 준다(푸시를 보낸 것 포함)")
    @GetMapping
    public ApiResponse<ContextQuestionListResponse> getQuestions(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "PENDING") QuestionStatus status) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                new ContextQuestionListResponse(contextQuestionService.getQuestions(userId, status)));
    }
}
