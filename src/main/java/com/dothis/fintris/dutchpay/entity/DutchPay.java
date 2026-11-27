package com.dothis.fintris.dutchpay.entity;

import com.dothis.fintris.global.entity.BaseEntity;
import com.dothis.fintris.user.entity.User;
import com.dothis.fintris.waitingfund.entity.WaitingFund;
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

    private Long myShare; // 사용자가 정한 내 몫 합. null이면 결제 합 - 받은 돈

    // 받은 돈 중 블록에 돌려놓고 남은 돈(올림해서 보낸 차액, 섞여 들어온 내 돈)을 넣은 나눌 돈
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiting_fund_id")
    private WaitingFund waitingFund;

    private Long bufferAmount; // 그 나눌 돈에 넣은 금액. 다시 계산할 때 차이만큼만 더하고 뺀다

    // 블록 잔액을 같이 바꾸므로 동시 수정을 막는다
    @Version
    private Long version;

    public void changeMyShare(Long myShare) {
        this.myShare = myShare;
    }

    public void applyBuffer(WaitingFund waitingFund, long bufferAmount) {
        this.waitingFund = waitingFund;
        this.bufferAmount = bufferAmount;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
