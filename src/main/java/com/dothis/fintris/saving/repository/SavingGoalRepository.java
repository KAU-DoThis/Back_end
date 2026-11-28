package com.dothis.fintris.saving.repository;

import com.dothis.fintris.saving.entity.SavingGoal;
import com.dothis.fintris.saving.entity.SavingGoalStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SavingGoalRepository extends JpaRepository<SavingGoal, Long> {

    // 같은 카테고리에는 진행 중인 목표를 하나만 둔다
    boolean existsByUser_IdAndCategory_IdAndStatus(Long userId, Long categoryId, SavingGoalStatus status);

    @EntityGraph(attributePaths = {"category", "linkedBlock"})
    List<SavingGoal> findAllByUser_IdAndStatusInOrderByCreatedAtDescIdDesc(Long userId,
                                                                          Collection<SavingGoalStatus> statuses);

    // 월간 리포트: 그 달에 끝나 확정된 목표
    @EntityGraph(attributePaths = "category")
    List<SavingGoal> findAllByUser_IdAndStatusAndEndDateBetween(Long userId, SavingGoalStatus status,
                                                                LocalDate from, LocalDate to);

    // 확정 대상: 기간이 끝난(종료일이 오늘 이전) 진행 중 목표
    @EntityGraph(attributePaths = {"user", "category"})
    List<SavingGoal> findAllByStatusAndEndDateBefore(SavingGoalStatus status, LocalDate today);
}
