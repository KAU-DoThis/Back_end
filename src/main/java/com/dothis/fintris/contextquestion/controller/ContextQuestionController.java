package com.dothis.fintris.contextquestion.controller;

import com.dothis.fintris.contextquestion.dto.ContextAnswerRequest;
import com.dothis.fintris.contextquestion.dto.ContextAnswerResponse;
import com.dothis.fintris.contextquestion.dto.ContextQuestionListResponse;
import com.dothis.fintris.contextquestion.entity.QuestionStatus;
import com.dothis.fintris.contextquestion.service.ContextQuestionService;
import com.dothis.fintris.global.api.ApiResponse;
import com.dothis.fintris.global.api.code.GeneralSuccessCode;
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

    @Operation(summary = "맥락 질문 답변",
            description = "tag는 선택지 중 하나. 야간 배달 질문(LATE_NIGHT_FOOD)은 답만 거래에 남기고, "
                    + "나머지는 고른 카테고리로 확정한 뒤 블록 반영을 다시 맞춘다. 이미 답한 질문이면 409")
    @PostMapping("/{questionId}/answer")
    public ApiResponse<ContextAnswerResponse> answer(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long questionId,
                                                     @RequestBody @Valid ContextAnswerRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                contextQuestionService.answer(userId, questionId, request.tag()));
    }
}
