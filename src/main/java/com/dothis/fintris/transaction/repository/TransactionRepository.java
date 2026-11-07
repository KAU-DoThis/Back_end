package com.dothis.fintris.transaction.repository;

import com.dothis.fintris.transaction.entity.Transaction;
import com.dothis.fintris.transaction.entity.TransactionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

// 목록 조회의 linkedAccountId·blockId·status는 null이면 조건을 걸지 않는다
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("select t.externalKey from Transaction t where t.externalKey in :keys")
    List<String> findExistingExternalKeys(@Param("keys") Collection<String> keys);

    @EntityGraph(attributePaths = {"linkedAccount", "category", "block"})
    @Query("""
            select t from Transaction t
            where t.user.id = :userId and t.occurredAt >= :from and t.occurredAt < :to
              and (:linkedAccountId is null or t.linkedAccount.id = :linkedAccountId)
              and (:blockId is null or t.block.id = :blockId)
              and (:status is null or t.status = :status)
            order by t.occurredAt desc, t.id desc
            """)
    Slice<Transaction> findPage(@Param("userId") Long userId,
                                @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                @Param("linkedAccountId") Long linkedAccountId, @Param("blockId") Long blockId,
                                @Param("status") TransactionStatus status, Pageable pageable);

    // 취소된 거래는 빼고, 소비 제외 거래는 금액 합계에서만 뺀다
    @Query("""
            select new com.dothis.fintris.transaction.repository.TransactionTotals(
              coalesce(sum(case when t.direction = com.dothis.fintris.transaction.entity.TransactionDirection.OUT
                                 and t.excluded = false then t.amount else 0 end), 0),
              coalesce(sum(case when t.direction = com.dothis.fintris.transaction.entity.TransactionDirection.IN
                                 and t.excluded = false then t.amount else 0 end), 0),
              coalesce(sum(case when t.status = com.dothis.fintris.transaction.entity.TransactionStatus.PROVISIONAL
                                 then 1 else 0 end), 0),
              coalesce(sum(case when t.status = com.dothis.fintris.transaction.entity.TransactionStatus.NEEDS_REVIEW
                                 then 1 else 0 end), 0))
            from Transaction t
            where t.user.id = :userId and t.occurredAt >= :from and t.occurredAt < :to and t.isCanceled = false
              and (:linkedAccountId is null or t.linkedAccount.id = :linkedAccountId)
              and (:blockId is null or t.block.id = :blockId)
            """)
    TransactionTotals sumTotals(@Param("userId") Long userId,
                                @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                @Param("linkedAccountId") Long linkedAccountId, @Param("blockId") Long blockId);

    // 날짜별 입금 − 출금. 취소·소비 제외 거래는 빼고 목록과 같은 조건을 건다
    @Query("""
            select new com.dothis.fintris.transaction.repository.DayTotal(
              cast(t.occurredAt as LocalDate),
              coalesce(sum(case when t.direction = com.dothis.fintris.transaction.entity.TransactionDirection.IN
                                then t.amount else -t.amount end), 0))
            from Transaction t
            where t.user.id = :userId and t.occurredAt >= :from and t.occurredAt < :to
              and t.isCanceled = false and t.excluded = false
              and (:linkedAccountId is null or t.linkedAccount.id = :linkedAccountId)
              and (:blockId is null or t.block.id = :blockId)
              and (:status is null or t.status = :status)
            group by cast(t.occurredAt as LocalDate)
            """)
    List<DayTotal> sumByDay(@Param("userId") Long userId,
                            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                            @Param("linkedAccountId") Long linkedAccountId, @Param("blockId") Long blockId,
                            @Param("status") TransactionStatus status);
}
