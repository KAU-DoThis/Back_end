package com.dothis.fintris.contextquestion.service;

import com.dothis.fintris.contextquestion.code.ContextQuestionErrorCode;
import com.dothis.fintris.contextquestion.code.ContextRuleErrorCode;
import com.dothis.fintris.contextquestion.dto.ContextRuleResponse;
import com.dothis.fintris.contextquestion.entity.ContextQuestion;
import com.dothis.fintris.contextquestion.entity.ContextRule;
import com.dothis.fintris.contextquestion.entity.QuestionStatus;
import com.dothis.fintris.contextquestion.entity.QuestionType;
import com.dothis.fintris.contextquestion.entity.RuleMatch;
import com.dothis.fintris.contextquestion.repository.ContextQuestionRepository;
import com.dothis.fintris.contextquestion.repository.ContextRuleRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// "다음부터 묻지 않기" 규칙 저장·목록·삭제 (F-15)
// TODO: 질문을 만드는 곳(AI 분류 연동)이 붙으면 맞는 규칙이 있을 때 질문 대신 규칙의 답을 바로 적용한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContextRuleService {

    private final ContextRuleRepository contextRuleRepository;
    private final ContextQuestionRepository contextQuestionRepository;

    @Transactional
    public ContextRuleResponse create(Long userId, Long questionId) {
        ContextQuestion question = contextQuestionRepository.findById(questionId)
                .orElseThrow(() -> GeneralException.of(ContextQuestionErrorCode.CONTEXT_QUESTION_NOT_FOUND));
        if (!question.isOwnedBy(userId)) {
            throw GeneralException.of(ContextQuestionErrorCode.CONTEXT_QUESTION_FORBIDDEN);
        }
        if (question.getStatus() != QuestionStatus.ANSWERED) {
            throw GeneralException.of(ContextRuleErrorCode.CONTEXT_RULE_NOT_ANSWERED);
        }
        RuleMatch match = RuleMatch.of(question.getType(), question.getTransaction());
        if (match == null) {
            throw GeneralException.of(ContextRuleErrorCode.CONTEXT_RULE_NO_MATCH);
        }
        boolean duplicate = contextRuleRepository.findAllByUser_IdAndQuestionType(userId, question.getType()).stream()
                .anyMatch(rule -> match.equals(rule.getMatch()));
        if (duplicate) {
            throw GeneralException.of(ContextRuleErrorCode.CONTEXT_RULE_DUPLICATE);
        }

        // 카테고리 답은 답할 때 거래에 이미 반영돼 있다
        Transaction transaction = question.getTransaction();
        boolean lateNight = question.getType() == QuestionType.LATE_NIGHT_FOOD;
        ContextRule rule = contextRuleRepository.save(ContextRule.builder()
                .user(question.getUser())
                .questionType(question.getType())
                .match(match)
                .category(lateNight ? null : transaction.getCategory())
                .tag(lateNight ? question.getAnswerTag() : null)
                .build());
        return ContextRuleResponse.from(rule);
    }

    public List<ContextRuleResponse> getRules(Long userId) {
        return contextRuleRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(ContextRuleResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long ruleId) {
        ContextRule rule = contextRuleRepository.findById(ruleId)
                .orElseThrow(() -> GeneralException.of(ContextRuleErrorCode.CONTEXT_RULE_NOT_FOUND));
        if (!rule.isOwnedBy(userId)) {
            throw GeneralException.of(ContextRuleErrorCode.CONTEXT_RULE_FORBIDDEN);
        }
        contextRuleRepository.delete(rule);
    }
}
