package com.dothis.fintris.dutchpay.entity;

import com.dothis.fintris.block.entity.Block;
import com.dothis.fintris.global.entity.BaseEntity;
import com.dothis.fintris.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

// 더치페이 묶음 (N-06). 결제와 받은 돈은 DutchPayTransaction으로 연결하고 거래 자체는 바꾸지 않는다
@Entity
@Table(name = "dutch_pays")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DutchPay extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dutch_pay_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 받은 돈을 돌려놓은 블록. 가장 큰 결제가 블록에 반영되지 않았으면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id")
    private Block block;

    // 그 블록에 더한 금액. 수정·해제할 때 이만큼 되돌린다
    @Column(nullable = false)
    private long returnedAmount;

    // 블록 잔액을 같이 바꾸므로 동시 수정을 막는다
    @Version
    private Long version;

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    // 블록 잔액은 서비스가 바꾸고, 여기는 어디에 얼마를 돌려놓았는지만 남긴다
    public void returnTo(Block block, long amount) {
        this.block = block;
        this.returnedAmount = amount;
    }
}
