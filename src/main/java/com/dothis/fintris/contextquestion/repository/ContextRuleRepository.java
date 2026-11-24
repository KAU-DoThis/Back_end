package com.dothis.fintris.contextquestion.repository;

import com.dothis.fintris.contextquestion.entity.ContextRule;
import com.dothis.fintris.contextquestion.entity.QuestionType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContextRuleRepository extends JpaRepository<ContextRule, Long> {

    // match는 JSON이라 같은 유형 규칙을 가져와 자바에서 비교한다
    List<ContextRule> findAllByUser_IdAndQuestionType(Long userId, QuestionType questionType);

    @EntityGraph(attributePaths = {"category"})
    List<ContextRule> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
}
