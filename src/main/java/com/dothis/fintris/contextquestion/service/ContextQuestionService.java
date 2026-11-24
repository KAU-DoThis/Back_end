package com.dothis.fintris.contextquestion.service;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.contextquestion.code.ContextQuestionErrorCode;
import com.dothis.fintris.contextquestion.dto.ContextAnswerResponse;
import com.dothis.fintris.contextquestion.dto.ContextQuestionResponse;
import com.dothis.fintris.contextquestion.dto.ContextQuestionResponse.Option;
import com.dothis.fintris.contextquestion.entity.ContextQuestion;
import com.dothis.fintris.contextquestion.entity.QuestionStatus;
import com.dothis.fintris.contextquestion.entity.QuestionType;
import com.dothis.fintris.contextquestion.repository.ContextQuestionRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.dto.TransactionResponse;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.service.BlockReflector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final BlockReflector blockReflector;

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

    // 야간 배달 답은 맥락만 남기고, 나머지는 고른 카테고리로 확정한 뒤 블록 반영을 다시 맞춘다
    // TODO: 더치페이로 답하면 더치페이 만들기로 이어 준다 (N-06-3, 더치페이 머지 후)
    @Transactional
    public ContextAnswerResponse answer(Long userId, Long questionId, String tag) {
        ContextQuestion question = contextQuestionRepository.findById(questionId)
                .orElseThrow(() -> GeneralException.of(ContextQuestionErrorCode.CONTEXT_QUESTION_NOT_FOUND));
        if (!question.isOwnedBy(userId)) {
            throw GeneralException.of(ContextQuestionErrorCode.CONTEXT_QUESTION_FORBIDDEN);
        }
        if (!question.isOpen()) {
            throw GeneralException.of(ContextQuestionErrorCode.CONTEXT_QUESTION_CLOSED);
        }
        if (!question.getOptions().contains(tag)) {
            throw GeneralException.of(ContextQuestionErrorCode.CONTEXT_QUESTION_INVALID_OPTION);
        }

        Transaction transaction = question.getTransaction();
        String resultText;
        if (question.getType() == QuestionType.LATE_NIGHT_FOOD) {
            transaction.tagContext(tag);
            resultText = ContextQuestionText.CONTEXT_SAVED;
        } else {
            // 선택지는 AI가 준 카테고리 코드라 없으면 시드와 AI 사전이 어긋난 것이다
            Category category = categoryRepository.findByCode(tag)
                    .orElseThrow(() -> GeneralException.of(ContextQuestionErrorCode.CONTEXT_QUESTION_INVALID_OPTION));
            Block before = transaction.getBlock();
            transaction.changeCategoryByUser(category);
            transaction.confirmReview();
            blockReflector.reconcile(transaction, blockReflector.blocksConfirmedAt(userId).orElse(null));
            resultText = ContextQuestionText.categoryChanged(category.getName(), before, transaction.getBlock());
        }
        question.answer(tag, LocalDateTime.now(KST));
        return new ContextAnswerResponse(question.getId(), tag, resultText, TransactionResponse.from(transaction));
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
