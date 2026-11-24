package com.dothis.fintris.contextquestion.repository;

import com.dothis.fintris.contextquestion.entity.ContextQuestion;
import com.dothis.fintris.contextquestion.entity.QuestionStatus;
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

    @EntityGraph(attributePaths = {"transaction", "transaction.linkedAccount"})
    List<ContextQuestion> findAllByUser_IdAndStatusInOrderByTransaction_OccurredAtDesc(
            Long userId, Collection<QuestionStatus> statuses);
}
