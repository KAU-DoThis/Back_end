package com.dothis.fintris.block.entity;

import com.dothis.fintris.global.entity.BaseCreatedEntity;
import com.dothis.fintris.waitingfund.entity.WaitingFund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

// 블록·나눌 돈 사이 이동 기록. 보내는 쪽과 받는 쪽은 각각 블록이나 나눌 돈 중 하나다
@Entity
@Table(name = "block_transfers")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockTransfer extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "block_transfer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_block_id")
    private Block fromBlock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_block_id")
    private Block toBlock;

    // 나눌 돈에서 블록으로(배분) 또는 블록에서 나눌 돈으로(삭제 반환) 옮긴 경우
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_waiting_fund_id")
    private WaitingFund fromWaitingFund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_waiting_fund_id")
    private WaitingFund toWaitingFund;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferReason reason;
}
