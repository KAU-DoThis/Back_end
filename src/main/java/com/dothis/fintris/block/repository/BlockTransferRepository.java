package com.dothis.fintris.block.repository;

import com.dothis.fintris.block.entity.BlockTransfer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BlockTransferRepository extends JpaRepository<BlockTransfer, Long> {

    // 목표형 "이번 달 확보액" = 이번 달 들어온 돈 - 나간 돈
    // 블록을 만들 때 넣은 첫 배분(보내는 쪽 없음)은 이미 모아 둔 돈이라 이번 달 확보액에서 뺀다
    @Query("select coalesce(sum(t.amount), 0) from BlockTransfer t where t.toBlock.id = :blockId and t.createdAt >= :from "
            + "and (t.fromBlock is not null or t.fromWaitingFund is not null)")
    long sumInSince(@Param("blockId") Long blockId, @Param("from") LocalDateTime from);

    @Query("select coalesce(sum(t.amount), 0) from BlockTransfer t where t.fromBlock.id = :blockId and t.createdAt >= :from")
    long sumOutSince(@Param("blockId") Long blockId, @Param("from") LocalDateTime from);

    // 블록 기록 (02 화면 "들어온 돈", "전체 보기"). 들어오고 나간 이동을 최근 순으로
    @Query("select t from BlockTransfer t where t.toBlock.id = :blockId or t.fromBlock.id = :blockId "
            + "order by t.createdAt desc, t.id desc")
    Slice<BlockTransfer> findHistory(@Param("blockId") Long blockId, Pageable pageable);

    // 목표형 월별 누적 그래프용 (오래된 순)
    @Query("select t from BlockTransfer t where t.toBlock.id = :blockId or t.fromBlock.id = :blockId "
            + "order by t.createdAt asc, t.id asc")
    List<BlockTransfer> findAllOfBlock(@Param("blockId") Long blockId);
}
