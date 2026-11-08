package com.dothis.fintris.block.repository;

import com.dothis.fintris.block.entity.BlockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface BlockTransferRepository extends JpaRepository<BlockTransfer, Long> {

    // 목표형 "이번 달 확보액" = 이번 달 들어온 돈 - 나간 돈
    @Query("select coalesce(sum(t.amount), 0) from BlockTransfer t where t.toBlock.id = :blockId and t.createdAt >= :from")
    long sumInSince(@Param("blockId") Long blockId, @Param("from") LocalDateTime from);

    @Query("select coalesce(sum(t.amount), 0) from BlockTransfer t where t.fromBlock.id = :blockId and t.createdAt >= :from")
    long sumOutSince(@Param("blockId") Long blockId, @Param("from") LocalDateTime from);
}
