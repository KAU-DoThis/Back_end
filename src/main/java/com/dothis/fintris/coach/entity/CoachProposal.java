package com.dothis.fintris.coach.entity;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.global.entity.BaseEntity;
import com.dothis.fintris.user.entity.User;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// 초불이의 방 블록 이동 제안. 사용자가 수락해야 실제로 옮긴다 (F-16-2)
@Entity
@Table(name = "coach_proposals")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoachProposal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coach_proposal_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_block_id", nullable = false)
    private Block fromBlock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_block_id", nullable = false)
    private Block toBlock;

    @Column(nullable = false)
    private long amount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private TransferPreview preview;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposalStatus status = ProposalStatus.PENDING;

    private Long transferId;

    @JdbcTypeCode(SqlTypes.JSON)
    private TransferResult result;

    private Long undoTransferId;

    @JdbcTypeCode(SqlTypes.JSON)
    private TransferResult undoResult;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    // 수락 전인데 만료 시간이 지났으면 EXPIRED
    public ProposalStatus statusAt(LocalDateTime now) {
        if (status == ProposalStatus.PENDING && !now.isBefore(expiresAt)) {
            return ProposalStatus.EXPIRED;
        }
        return status;
    }

    public void accept(Long transferId, TransferResult result) {
        this.status = ProposalStatus.ACCEPTED;
        this.transferId = transferId;
        this.result = result;
    }

    public void reject() {
        this.status = ProposalStatus.REJECTED;
    }

    public void undo(Long undoTransferId, TransferResult undoResult) {
        this.status = ProposalStatus.UNDONE;
        this.undoTransferId = undoTransferId;
        this.undoResult = undoResult;
    }
}
