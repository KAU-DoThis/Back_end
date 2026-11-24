package com.dothis.fintris.contextquestion.service;

import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.contextquestion.dto.ContextQuestionResponse;
import com.dothis.fintris.contextquestion.dto.ContextQuestionResponse.Option;
import com.dothis.fintris.contextquestion.entity.ContextQuestion;
import com.dothis.fintris.contextquestion.entity.QuestionStatus;
import com.dothis.fintris.contextquestion.repository.ContextQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContextQuestionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String FREE_TEXT_NOTICE = "적은 내용은 분류에만 쓰고 저장하지 않아요";

    private final ContextQuestionRepository contextQuestionRepository;
    private final CategoryRepository categoryRepository;

    // PENDING은 "아직 답하지 않은 질문"이라 푸시를 보낸 것(SENT)도 함께 준다
    public List<ContextQuestionResponse> getQuestions(Long userId, QuestionStatus status) {
        List<QuestionStatus> statuses = status == QuestionStatus.PENDING
                ? List.of(QuestionStatus.PENDING, QuestionStatus.SENT)
                : List.of(status);
        List<ContextQuestion> questions = contextQuestionRepository
                .findAllByUser_IdAndStatusInOrderByTransaction_OccurredAtDesc(userId, statuses);
        if (questions.isEmpty()) {
            return List.of();
        }
        Map<String, String> categoryNames = categoryRepository.findAllByOrderByDisplayOrderAsc().stream()
                .collect(Collectors.toMap(Category::getCode, Category::getName));
        LocalDate today = LocalDate.now(KST);
        return questions.stream()
                .map(question -> toResponse(question, categoryNames, today))
                .toList();
    }

    private ContextQuestionResponse toResponse(ContextQuestion question, Map<String, String> categoryNames,
                                               LocalDate today) {
        // 카테고리 코드가 아닌 선택지(야간 배달 답)는 값 그대로 보여준다
        List<Option> options = question.getOptions().stream()
                .map(tag -> new Option(tag, categoryNames.getOrDefault(tag, tag)))
                .toList();
        return new ContextQuestionResponse(
                question.getId(),
                question.getTransaction().getId(),
                question.getType(),
                question.getStatus(),
                ContextQuestionText.text(question.getType(), question.getTransaction(), today),
                ContextQuestionText.meta(question.getTransaction()),
                options,
                false,
                FREE_TEXT_NOTICE
        );
    }
}
