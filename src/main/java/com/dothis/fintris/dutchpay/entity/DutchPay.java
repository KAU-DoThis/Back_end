package com.dothis.fintris.dutchpay.entity;

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

    // 블록 잔액을 같이 바꾸므로 동시 수정을 막는다
    @Version
    private Long version;

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
