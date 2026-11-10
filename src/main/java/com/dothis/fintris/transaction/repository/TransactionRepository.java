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

    @Query("select t.externalKey from Transaction t where t.user.id = :userId and t.externalKey in :keys")
    List<String> findExistingExternalKeys(@Param("userId") Long userId, @Param("keys") Collection<String> keys);

    // 카드 취소는 같은 키의 기존 거래를 고쳐야 해서 키가 아니라 거래를 가져온다
    List<Transaction> findAllByUser_IdAndExternalKeyIn(Long userId, Collection<String> keys);

    // 체크카드 중복 판정용. 은행·카드 구분이 필요해 계좌를 함께 가져온다
    @EntityGraph(attributePaths = "linkedAccount")
    @Query("""
            select t from Transaction t
            where t.user.id = :userId and t.occurredAt >= :from and t.isCanceled = false
              and t.direction = com.dothis.fintris.transaction.entity.TransactionDirection.OUT
            """)
    List<Transaction> findOutgoingSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    // 블록 반영을 다시 맞출 후보. 취소·소비 제외된 거래도 되돌려야 해서 함께 고른다
    @Query("""
            select t.id from Transaction t
            where t.user.id = :userId and t.occurredAt >= :from
              and t.direction = com.dothis.fintris.transaction.entity.TransactionDirection.OUT
            order by t.occurredAt, t.id
            """)
    List<Long> findOutgoingIdsSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    // 블록 상세: 이 블록에 반영된 최근 거래 (사용형 최근 거래, 고정지출형 납부 기록)
    @EntityGraph(attributePaths = {"linkedAccount", "category", "block"})
    List<Transaction> findTop12ByBlock_IdOrderByOccurredAtDescIdDesc(Long blockId);

    // 고정지출형 블록 기록 "납부 기록 전체 보기"
    @EntityGraph(attributePaths = "linkedAccount")
    Slice<Transaction> findByBlock_IdOrderByOccurredAtDescIdDesc(Long blockId, Pageable pageable);

    // 블록 상세 "오늘 쓴 돈": 거래 금액 합(spent)과 이 블록에서 실제로 뺀 금액 합(deducted)
    @Query("""
            select new com.dothis.fintris.transaction.repository.BlockSpent(
              coalesce(sum(t.amount), 0), coalesce(sum(t.blockAmount), 0))
            from Transaction t
            where t.block.id = :blockId and t.occurredAt >= :from and t.occurredAt < :to
            """)
    BlockSpent sumBlockSpent(@Param("blockId") Long blockId,
                             @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 블록 상세 소진 속도: 이 블록에 반영된 거래 금액의 날짜별 합
    @Query("""
            select new com.dothis.fintris.transaction.repository.DayTotal(
              cast(t.occurredAt as LocalDate), coalesce(sum(t.amount), 0))
            from Transaction t
            where t.block.id = :blockId and t.occurredAt >= :from and t.occurredAt < :to
            group by cast(t.occurredAt as LocalDate)
            """)
    List<DayTotal> sumBlockSpentByDay(@Param("blockId") Long blockId,
                                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

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

    // 취소된 거래는 빼고, 소비 제외 거래는 쓴 돈에서만 뺀다
    // v5에서는 입금도 소비 제외라 들어온 돈은 excluded 대신 본인 계좌 이동(SELF_TRANSFER)만 뺀다
    // TODO: 본인 계좌 이동 입금은 분류 전엔 카테고리가 없다. BE 판정(연동 계좌 간 같은 금액 출금·입금 짝 찾기)이 붙으면 함께 뺀다
    @Query("""
            select new com.dothis.fintris.transaction.repository.TransactionTotals(
              coalesce(sum(case when t.direction = com.dothis.fintris.transaction.entity.TransactionDirection.OUT
                                 and t.excluded = false then t.amount else 0 end), 0),
              coalesce(sum(case when t.direction = com.dothis.fintris.transaction.entity.TransactionDirection.IN
                                 and (c.code is null or c.code <> 'SELF_TRANSFER') then t.amount else 0 end), 0),
              coalesce(sum(case when t.status = com.dothis.fintris.transaction.entity.TransactionStatus.PROVISIONAL
                                 then 1 else 0 end), 0),
              coalesce(sum(case when t.status = com.dothis.fintris.transaction.entity.TransactionStatus.NEEDS_REVIEW
                                 then 1 else 0 end), 0))
            from Transaction t left join t.category c
            where t.user.id = :userId and t.occurredAt >= :from and t.occurredAt < :to and t.isCanceled = false
              and (:linkedAccountId is null or t.linkedAccount.id = :linkedAccountId)
              and (:blockId is null or t.block.id = :blockId)
            """)
    TransactionTotals sumTotals(@Param("userId") Long userId,
                                @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                @Param("linkedAccountId") Long linkedAccountId, @Param("blockId") Long blockId);

    // 소비 분석(F-01) 카테고리별 쓴 돈. 거래 목록의 쓴 돈과 같은 조건이라 합이 목록 합계와 맞는다
    @Query("""
            select new com.dothis.fintris.transaction.repository.CategorySpent(
              c.id, c.code, c.name, count(t), coalesce(sum(t.amount), 0))
            from Transaction t left join t.category c
            where t.user.id = :userId and t.occurredAt >= :from and t.occurredAt < :to
              and t.direction = com.dothis.fintris.transaction.entity.TransactionDirection.OUT
              and t.isCanceled = false and t.excluded = false
            group by c.id, c.code, c.name
            """)
    List<CategorySpent> sumSpentByCategory(@Param("userId") Long userId,
                                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 소비 분석 시간대별 횟수용. sumSpentByCategory와 같은 조건이다
    @Query("""
            select t.occurredAt from Transaction t
            where t.user.id = :userId and t.category.id = :categoryId
              and t.occurredAt >= :from and t.occurredAt < :to
              and t.direction = com.dothis.fintris.transaction.entity.TransactionDirection.OUT
              and t.isCanceled = false and t.excluded = false
            """)
    List<LocalDateTime> findSpentTimes(@Param("userId") Long userId, @Param("categoryId") Long categoryId,
                                       @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 날짜별 쓴 돈(출금 합계, 양수). 취소·소비 제외 거래는 빼고 목록과 같은 조건을 건다
    @Query("""
            select new com.dothis.fintris.transaction.repository.DayTotal(
              cast(t.occurredAt as LocalDate), coalesce(sum(t.amount), 0))
            from Transaction t
            where t.user.id = :userId and t.occurredAt >= :from and t.occurredAt < :to
              and t.direction = com.dothis.fintris.transaction.entity.TransactionDirection.OUT
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
