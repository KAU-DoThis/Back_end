package com.dothis.fintris.contextquestion.service;

import com.dothis.fintris.category.entity.Category;
import com.dothis.fintris.category.entity.CategoryType;
import com.dothis.fintris.category.repository.CategoryRepository;
import com.dothis.fintris.contextquestion.code.ContextQuestionErrorCode;
import com.dothis.fintris.contextquestion.dto.ContextAnswerResponse;
import com.dothis.fintris.contextquestion.entity.ContextQuestion;
import com.dothis.fintris.contextquestion.entity.QuestionStatus;
import com.dothis.fintris.contextquestion.entity.QuestionType;
import com.dothis.fintris.contextquestion.repository.ContextQuestionRepository;
import com.dothis.fintris.global.exception.GeneralException;
import com.dothis.fintris.transaction.entity.ClassifiedBy;
import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionDirection;
import com.dothis.fintris.transaction.entity.TransactionSource;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import com.dothis.fintris.transaction.service.BlockReflector;
import com.dothis.fintris.user.entity.AuthProvider;
import com.dothis.fintris.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class ContextQuestionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long QUESTION_ID = 56L;

    @Mock ContextQuestionRepository contextQuestionRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock BlockReflector blockReflector;
    @InjectMocks ContextQuestionService service;

    private User user(Long id) {
        User user = User.builder().email("user@example.com").authProvider(AuthProvider.EMAIL).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Transaction transaction(TransactionStatus status) {
        Transaction transaction = Transaction.builder().user(user(USER_ID))
                .occurredAt(LocalDateTime.of(2026, 10, 13, 23, 10)).amount(23_000L)
                .direction(TransactionDirection.OUT).merchantOrPayee("배달의민족")
                .source(TransactionSource.CODEF).status(status).build();
        ReflectionTestUtils.setField(transaction, "id", 120L);
        return transaction;
    }

    private ContextQuestion given(Long ownerId, QuestionType type, List<String> options, Transaction transaction) {
        ContextQuestion question = ContextQuestion.builder().user(user(ownerId)).transaction(transaction)
                .type(type).options(options).build();
        ReflectionTestUtils.setField(question, "id", QUESTION_ID);
        when(contextQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
        return question;
    }

    private Category living() {
        Category category = Category.builder().code("LIVING").name("생활/생필품")
                .categoryType(CategoryType.EXPENSE).displayOrder(7).build();
        ReflectionTestUtils.setField(category, "id", 7L);
        return category;
    }

    private ContextQuestionErrorCode errorOf(Runnable call) {
        try {
            call.run();
        } catch (GeneralException e) {
            return (ContextQuestionErrorCode) e.getCode();
        }
        throw new AssertionError("예외가 나지 않았다");
    }

    @Test
    @DisplayName("카테고리를 고르면 사용자 분류로 확정하고 블록 반영을 다시 맞춘다")
    void answerCategory() {
        Transaction transaction = transaction(TransactionStatus.NEEDS_REVIEW);
        ContextQuestion question = given(USER_ID, QuestionType.UNKNOWN_ITEM,
                List.of("LIVING", "FOOD", "SHOPPING", "ETC", "DUTCH_PAY"), transaction);
        when(categoryRepository.findByCode("LIVING")).thenReturn(Optional.of(living()));
        when(blockReflector.blocksConfirmedAt(USER_ID)).thenReturn(Optional.empty());

        ContextAnswerResponse response = service.answer(USER_ID, QUESTION_ID, "LIVING");

        assertThat(transaction.getCategory().getCode()).isEqualTo("LIVING");
        assertThat(transaction.getClassifiedBy()).isEqualTo(ClassifiedBy.USER);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.ANSWERED);
        assertThat(question.getAnswerTag()).isEqualTo("LIVING");
        assertThat(response.resultText()).isEqualTo("생활/생필품으로 바꿨어요");
        verify(blockReflector).reconcile(transaction, null);
    }

    @Test
    @DisplayName("야간 배달 질문은 카테고리를 건드리지 않고 답만 거래에 남긴다")
    void answerLateNightFood() {
        Transaction transaction = transaction(TransactionStatus.CONFIRMED);
        ContextQuestion question = given(USER_ID, QuestionType.LATE_NIGHT_FOOD, List.of("야근", "약속", "그냥"), transaction);

        ContextAnswerResponse response = service.answer(USER_ID, QUESTION_ID, "야근");

        assertThat(transaction.getContextTag()).isEqualTo("야근");
        assertThat(transaction.getCategory()).isNull();
        assertThat(question.getStatus()).isEqualTo(QuestionStatus.ANSWERED);
        assertThat(response.resultText()).isEqualTo(ContextQuestionText.CONTEXT_SAVED);
        verify(blockReflector, never()).reconcile(any(), any());
    }

    @Test
    @DisplayName("선택지에 없는 답이면 400")
    void answerInvalidOption() {
        given(USER_ID, QuestionType.UNKNOWN_ITEM, List.of("LIVING", "ETC"), transaction(TransactionStatus.NEEDS_REVIEW));

        assertThat(errorOf(() -> service.answer(USER_ID, QUESTION_ID, "CAFE")))
                .isEqualTo(ContextQuestionErrorCode.CONTEXT_QUESTION_INVALID_OPTION);
    }

    @Test
    @DisplayName("이미 답한 질문이면 409")
    void answerClosed() {
        ContextQuestion question = given(USER_ID, QuestionType.UNKNOWN_ITEM, List.of("LIVING"),
                transaction(TransactionStatus.CONFIRMED));
        question.answer("LIVING", LocalDateTime.of(2026, 10, 14, 9, 0));

        assertThat(errorOf(() -> service.answer(USER_ID, QUESTION_ID, "LIVING")))
                .isEqualTo(ContextQuestionErrorCode.CONTEXT_QUESTION_CLOSED);
    }

    @Test
    @DisplayName("다른 사용자의 질문이면 403")
    void answerForbidden() {
        given(2L, QuestionType.UNKNOWN_ITEM, List.of("LIVING"), transaction(TransactionStatus.NEEDS_REVIEW));

        assertThat(errorOf(() -> service.answer(USER_ID, QUESTION_ID, "LIVING")))
                .isEqualTo(ContextQuestionErrorCode.CONTEXT_QUESTION_FORBIDDEN);
    }

    @Test
    @DisplayName("없는 질문이면 404")
    void answerNotFound() {
        when(contextQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.answer(USER_ID, QUESTION_ID, "LIVING"))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ContextQuestionErrorCode.CONTEXT_QUESTION_NOT_FOUND);
    }
}
