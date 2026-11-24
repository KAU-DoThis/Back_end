package com.dothis.fintris.contextquestion.repository;

import com.dothis.fintris.contextquestion.entity.ContextQuestion;
import com.dothis.fintris.contextquestion.entity.QuestionStatus;
import com.dothis.fintris.contextquestion.entity.QuestionType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContextQuestionRepository extends JpaRepository<ContextQuestion, Long> {

    boolean existsByTransactionId(Long transactionId);

    @EntityGraph(attributePaths = {"transaction"})
    Optional<ContextQuestion> findFirstByUser_IdAndStatusInOrderByTransaction_OccurredAtDesc(
            Long userId, Collection<QuestionStatus> statuses);

    // 같은 답을 전에도 했는지 보려고 같은 유형·같은 답의 다른 질문을 가져온다
    @EntityGraph(attributePaths = {"transaction"})
    List<ContextQuestion> findAllByUser_IdAndTypeAndAnswerTagAndIdNot(
            Long userId, QuestionType type, String answerTag, Long id);

    @EntityGraph(attributePaths = {"transaction", "transaction.linkedAccount"})
    List<ContextQuestion> findAllByUser_IdAndStatusInOrderByTransaction_OccurredAtDesc(
            Long userId, Collection<QuestionStatus> statuses);
}
