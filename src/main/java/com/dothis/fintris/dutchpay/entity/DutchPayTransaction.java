package com.dothis.fintris.dutchpay.entity;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.global.entity.BaseCreatedEntity;
import com.dothis.fintris.transaction.entity.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

// 더치페이에 묶인 거래 하나. 결제(OUT)인지 받은 돈(IN)인지는 거래 방향으로 구분한다
// 거래 하나는 더치페이 하나에만 들어간다
@Entity
@Table(
        name = "dutch_pay_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dutch_pay_transactions_transaction",
                columnNames = "transaction_id"
        )
)
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DutchPayTransaction extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dutch_pay_transaction_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dutch_pay_id", nullable = false)
    private DutchPay dutchPay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    // 결제 건의 내 몫. 받은 돈을 결제 금액 비율로 나눠 뺀 값이고, 받은 돈 건은 null
    private Long myShare;

    // 결제 건에서 받은 돈(결제 금액 - 내 몫)을 돌려놓은 블록과 금액. 결제가 블록에 반영되지 않았거나 받은 돈 건이면 null
    // 수정·해제할 때 이만큼 되돌린다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id")
    private Block block;

    private Long returnedAmount;
}
