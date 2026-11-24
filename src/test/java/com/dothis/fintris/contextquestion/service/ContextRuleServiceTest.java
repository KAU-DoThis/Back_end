package com.dothis.fintris.contextquestion.service;

import com.dothis.fintris.analysis.service.TimeSlot;
import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.contextquestion.code.ContextRuleErrorCode;
import com.dothis.fintris.contextquestion.dto.ContextRuleResponse;
import com.dothis.fintris.contextquestion.entity.ContextQuestion;
import com.dothis.fintris.contextquestion.entity.ContextRule;
import com.dothis.fintris.contextquestion.entity.QuestionType;
import com.dothis.fintris.contextquestion.entity.RuleMatch;
import com.dothis.fintris.contextquestion.repository.ContextQuestionRepository;
import com.dothis.fintris.contextquestion.repository.ContextRuleRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextRuleServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long QUESTION_ID = 56L;

    @Mock ContextRuleRepository contextRuleRepository;
    @Mock ContextQuestionRepository contextQuestionRepository;
    @InjectMocks ContextRuleService service;

    private User user(Long id) {
        User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Category living() {
        return Category.builder().code("LIVING").name("생활/생필품").categoryType(CategoryType.EXPENSE).displayOrder(7).build();
    }

    private ContextQuestion question(QuestionType type, String merchant, Category category, String answer) {
        Transaction transaction = Transaction.builder().user(user(USER_ID))
                .occurredAt(LocalDateTime.of(2026, 10, 13, 19, 22)).amount(38_000L)
                .direction(TransactionDirection.OUT).merchantOrPayee(merchant).category(category)
                .source(TransactionSource.CODEF).status(TransactionStatus.CONFIRMED).build();
        ContextQuestion question = ContextQuestion.builder().user(user(USER_ID)).transaction(transaction)
                .type(type).options(List.of("LIVING", "ETC")).build();
        if (answer != null) {
            question.answer(answer, LocalDateTime.of(2026, 10, 14, 9, 0));
        }
        ReflectionTestUtils.setField(question, "id", QUESTION_ID);
        when(contextQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
        return question;
    }

    private ContextRuleErrorCode errorOf(Runnable call) {
        try {
            call.run();
        } catch (GeneralException e) {
            return (ContextRuleErrorCode) e.getCode();
        }
        throw new AssertionError("예외가 나지 않았다");
    }

    @Test
    @DisplayName("답한 질문의 가맹점·시간대와 거래 카테고리로 규칙을 만든다")
    void createCategoryRule() {
        question(QuestionType.UNKNOWN_ITEM, "네이버페이", living(), "LIVING");
        when(contextRuleRepository.save(any(ContextRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContextRuleResponse response = service.create(USER_ID, QUESTION_ID);

        ArgumentCaptor<ContextRule> saved = ArgumentCaptor.captor();
        verify(contextRuleRepository).save(saved.capture());
        assertThat(saved.getValue().getMatch()).isEqualTo(new RuleMatch("네이버페이", TimeSlot.EVENING, null));
        assertThat(saved.getValue().getTag()).isNull();
        assertThat(response.target()).isEqualTo("네이버페이 · 저녁 15~22시");
        assertThat(response.answer()).isEqualTo("생활/생필품");
    }

    @Test
    @DisplayName("야간 배달 규칙은 카테고리 없이 고른 답을 저장한다")
    void createLateNightRule() {
        question(QuestionType.LATE_NIGHT_FOOD, "배달의민족", null, "야근");
        when(contextRuleRepository.save(any(ContextRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContextRuleResponse response = service.create(USER_ID, QUESTION_ID);

        assertThat(response.answer()).isEqualTo("야근");
    }

    @Test
    @DisplayName("개인 송금 규칙은 받는 사람으로 맞춘다")
    void createTransferRule() {
        question(QuestionType.PERSONAL_TRANSFER, "김*수", null, "DUTCH_PAY");
        when(contextRuleRepository.save(any(ContextRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(USER_ID, QUESTION_ID).target()).isEqualTo("김*수에게 보낸 돈");
    }

    @Test
    @DisplayName("아직 답하지 않은 질문으로는 만들 수 없다")
    void createNotAnswered() {
        question(QuestionType.UNKNOWN_ITEM, "네이버페이", null, null);

        assertThat(errorOf(() -> service.create(USER_ID, QUESTION_ID)))
                .isEqualTo(ContextRuleErrorCode.CONTEXT_RULE_NOT_ANSWERED);
    }

    @Test
    @DisplayName("가맹점 이름이 없는 거래는 규칙으로 만들 수 없다")
    void createNoMatch() {
        question(QuestionType.UNKNOWN_ITEM, null, living(), "LIVING");

        assertThat(errorOf(() -> service.create(USER_ID, QUESTION_ID)))
                .isEqualTo(ContextRuleErrorCode.CONTEXT_RULE_NO_MATCH);
    }

    @Test
    @DisplayName("같은 규칙이 이미 있으면 409")
    void createDuplicate() {
        question(QuestionType.UNKNOWN_ITEM, "네이버페이", living(), "LIVING");
        ContextRule existing = ContextRule.builder().user(user(USER_ID)).questionType(QuestionType.UNKNOWN_ITEM)
                .match(new RuleMatch("네이버페이", TimeSlot.EVENING, null)).category(living()).build();
        when(contextRuleRepository.findAllByUser_IdAndQuestionType(USER_ID, QuestionType.UNKNOWN_ITEM))
                .thenReturn(List.of(existing));

        assertThat(errorOf(() -> service.create(USER_ID, QUESTION_ID)))
                .isEqualTo(ContextRuleErrorCode.CONTEXT_RULE_DUPLICATE);
        verify(contextRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 사용자의 규칙은 지울 수 없다")
    void deleteForbidden() {
        ContextRule rule = ContextRule.builder().user(user(2L)).questionType(QuestionType.UNKNOWN_ITEM)
                .match(new RuleMatch("네이버페이", TimeSlot.EVENING, null)).build();
        when(contextRuleRepository.findById(3L)).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> service.delete(USER_ID, 3L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ContextRuleErrorCode.CONTEXT_RULE_FORBIDDEN);
        verify(contextRuleRepository, never()).delete(any());
    }
}
